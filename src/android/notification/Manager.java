/*
 * Apache 2.0 License
 *
 * Copyright (c) Sebastian Katzer 2017
 *
 * This file contains Original Code and/or Modifications of Original Code
 * as defined in and that are subject to the Apache License
 * Version 2.0 (the 'License'). You may not use this file except in
 * compliance with the License. Please obtain a copy of the License at
 * http://opensource.org/licenses/Apache-2.0/ and read it before using this
 * file.
 *
 * The Original Code and all software distributed under the License are
 * distributed on an 'AS IS' basis, WITHOUT WARRANTY OF ANY KIND, EITHER
 * EXPRESS OR IMPLIED, AND APPLE HEREBY DISCLAIMS ALL SUCH WARRANTIES,
 * INCLUDING WITHOUT LIMITATION, ANY WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE, QUIET ENJOYMENT OR NON-INFRINGEMENT.
 * Please see the License for the specific language governing rights and
 * limitations under the License.
 */

// codebeat:disable[TOO_MANY_FUNCTIONS]

package de.appplant.cordova.plugin.localnotification.notification;

import android.annotation.SuppressLint;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.service.notification.StatusBarNotification;
import androidx.core.app.NotificationManagerCompat;
import android.app.AlarmManager;
import android.media.AudioAttributes;
import android.net.Uri;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import de.appplant.cordova.plugin.badge.BadgeImpl;

import static android.os.Build.VERSION.SDK_INT;
import static android.os.Build.VERSION_CODES.M;
import static android.os.Build.VERSION_CODES.O;
import static android.os.Build.VERSION_CODES.S;
import static androidx.core.app.NotificationManagerCompat.IMPORTANCE_DEFAULT;
import static de.appplant.cordova.plugin.localnotification.notification.Notification.PREF_KEY_ID;
import static de.appplant.cordova.plugin.localnotification.notification.Notification.Type.TRIGGERED;
import de.appplant.cordova.plugin.localnotification.notification.util.AssetUtil;

/**
 * Central way to access all or single local notifications set by specific
 * state like triggered or scheduled. Offers shortcut ways to schedule,
 * cancel or clear local notifications.
 */
public final class Manager {

    public static final String TAG = "Manager";

    // TODO: temporary
    static final String CHANNEL_ID = "default-channel-id";

    // TODO: temporary
    private static final CharSequence CHANNEL_NAME = "Default channel";

    // The application context
    private Context context;

    /**
     * Constructor
     *
     * @param context Application context
     */
    private Manager(Context context) {
        this.context = context;
        createDefaultChannel();
    }

    /**
     * Static method to retrieve class instance.
     *
     * @param context Application context
     */
    public static Manager getInstance(Context context) {
        return new Manager(context);
    }

    /**
     * Ask if user has enabled permission for local notifications.
     */
    public boolean areNotificationsEnabled() {
        return getNotCompMgr().areNotificationsEnabled();
    }

    /**
     * Check if the setting to schedule exact alarms is enabled.
     */
    public boolean canScheduleExactAlarms() {
        // Supported since Android 12
        if (SDK_INT < S) return true;
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        return alarmManager.canScheduleExactAlarms();
    }

    /**
     * Schedule local notification specified by request.
     *
     * @param request Set of notification options.
     */
    public Notification schedule(Request request) {
        Options options    = request.getOptions();
        Notification toast = new Notification(context, options);

        toast.schedule(request);

        return toast;
    }

    /**
     * TODO: temporary
     */
    @SuppressLint("WrongConstant")
    private void createDefaultChannel() {
        NotificationManager mgr = getNotMgr();

        if (SDK_INT < O)
            return;

        NotificationChannel channel = mgr.getNotificationChannel(CHANNEL_ID);

        if (channel != null)
            return;

        channel = new NotificationChannel(
                CHANNEL_ID, CHANNEL_NAME, IMPORTANCE_DEFAULT);

        mgr.createNotificationChannel(channel);
    }

    /**
     * Create Notification channel with options
     * @param options Set of channel options.
     * 
     */
    public void createChannel(JSONObject options) {
        // Channels are supported since Android 8
        if (SDK_INT < O) return;

        NotificationManager mgr = getNotMgr();

        String channelId = options.optString("channelId", "");
        CharSequence channelName = options.optString("channelName", "");
        int importance = options.optInt("importance", IMPORTANCE_DEFAULT);

        NotificationChannel channel = mgr.getNotificationChannel(channelId);
        
        // Channel already created
        if (channel != null) return;
        
        channel = new NotificationChannel(channelId, channelName, importance);

        if (options.has("vibrate")) {
            Boolean shouldVibrate = options.optBoolean("vibrate", false);
            channel.enableVibration(shouldVibrate);
        }

        if (options.has("sound")) {
            AssetUtil assets = AssetUtil.getInstance(this.context);
            Uri soundUri = assets.parse(options.optString("sound", null));
    
            if (!soundUri.equals(Uri.EMPTY)) {
                AudioAttributes attributes = new AudioAttributes.Builder().setUsage(
                    options.optInt("soundUsage", AudioAttributes.USAGE_NOTIFICATION)).build();
                channel.setSound(soundUri, attributes);
            } else {
                channel.setSound(null, null);
            }
        }

        if (options.has("description")) {
            channel.setDescription(options.optString("description", " "));
        }

        mgr.createNotificationChannel(channel);
    }

    /**
     * Deletes a notification channel by an id. If you create a new channel with this same id,
     * the deleted channel will be un-deleted with all of the same settings it had before it was deleted.
     * @param channelId Like "my_channel_id"
     */
    public void deleteChannel(String channelId) {
        // Channels are supported since Android 8
        if (SDK_INT < O) return;

        Log.d(TAG, "Delete channel, id=" + channelId);

        // Cancel all notifications regarding this channel
        for (Notification notification : Manager.getInstance(context).getNotifications()) {
            if (notification.getOptions().getChannelId().equals(channelId)) {
                notification.cancel();
            }
        }

        getNotMgr().deleteNotificationChannel(channelId);
    }

    /**
     * Update local notification specified by ID.
     *
     * @param id       The notification ID.
     * @param updates  JSON object with notification options.
     */
    public Notification update(int id, JSONObject updates) {
        Notification notification = get(id);

        if (notification == null) return null;

        notification.update(updates);

        return notification;
    }

    /**
     * Clear local notification specified by ID.
     *
     * @param id The notification ID.
     */
    public Notification clear(int id) {
        Notification toast = get(id);

        if (toast != null) {
            toast.clear();
        }

        return toast;
    }

    /**
     * Clear all local notifications.
     */
    public void clearAll() {
        List<Notification> toasts = getByType(TRIGGERED);

        for (Notification toast : toasts) {
            toast.clear();
        }

        getNotCompMgr().cancelAll();
        setBadge(0);
    }

    /**
     * Clear local notification specified by ID.
     *
     * @param id The notification ID
     */
    public Notification cancel(int id) {
        Notification toast = get(id);

        if (toast != null) {
            toast.cancel();
        }

        return toast;
    }

    /**
     * Cancel all local notifications.
     */
    public void cancelAll() {
        List<Notification> notifications = getNotifications();

        for (Notification notification : notifications) {
            notification.cancel();
        }

        getNotCompMgr().cancelAll();
        setBadge(0);
    }

    public boolean idExists(int id) {
        return getPrefs().contains(Integer.toString(id));
    }

    /**
     * All local notifications IDs.
     */
    public List<Integer> getIds() {
        Set<String> keys = getPrefs().getAll().keySet();
        List<Integer> ids = new ArrayList<Integer>();

        for (String key : keys) {
            try {
                ids.add(Integer.parseInt(key));
            } catch (NumberFormatException e) {
                e.printStackTrace();
            }
        }

        return ids;
    }

    /**
     * All local notification IDs for given type.
     *
     * @param type The notification life cycle type
     */
    public List<Integer> getIdsByType(Notification.Type type) {

        if (type == Notification.Type.ALL)
            return getIds();

        StatusBarNotification[] activeToasts = getActiveNotifications();
        List<Integer> activeIds              = new ArrayList<Integer>();

        for (StatusBarNotification toast : activeToasts) {
            activeIds.add(toast.getId());
        }

        if (type == TRIGGERED)
            return activeIds;

        List<Integer> ids = getIds();
        ids.removeAll(activeIds);

        return ids;
    }

    /**
     * List of all local notification.
     */
    public List<Notification> getNotifications() {
        return getNotifications(getIds());
    }

    /**
     * List of local notifications with matching ID.
     *
     * @param ids Set of notification IDs.
     */
    private List<Notification> getNotifications(List<Integer> ids) {
        List<Notification> toasts = new ArrayList<Notification>();

        for (int id : ids) {
            if (idExists(id)) toasts.add(get(id));
        }

        return toasts;
    }

    /**
     * List of local notifications from given type.
     *
     * @param type The notification life cycle type
     */
    public List<Notification> getByType(Notification.Type type) {
        return type == Notification.Type.ALL ? getNotifications() : getNotifications(getIdsByType(type));
    }

    /**
     * List of properties from all local notifications.
     */
    public List<JSONObject> getOptions() {
        return getOptionsById(getIds());
    }

    /**
     * List of properties from local notifications with matching ID.
     *
     * @param ids Set of notification IDs
     */
    public List<JSONObject> getOptionsById(List<Integer> ids) {
        List<JSONObject> toasts = new ArrayList<JSONObject>();

        for (int id : ids) {
            Options options = getOptions(id);

            if (options != null) {
                toasts.add(options.getDict());
            }
        }

        return toasts;
    }

    /**
     * List of properties from all local notifications from given type.
     *
     * @param type
     *      The notification life cycle type
     */
    public List<JSONObject> getOptionsByType(Notification.Type type) {
        ArrayList<JSONObject> options = new ArrayList<JSONObject>();
        List<Notification> notifications = getByType(type);

        for (Notification notification : notifications) {
            options.add(notification.getOptions().getDict());
        }

        return options;
    }

    /**
     * Get local notification options.
     *
     * @param id Notification ID.
     *
     * @return null if could not found.
     */
    public Options getOptions(int id) {
        if (!idExists(id)) return null;

        try {
            return new Options(context, new JSONObject(getPrefs().getString(Integer.toString(id), null)));
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Get existent local notification.
     *
     * @param id Notification ID.
     *
     * @return null if could not found.
     */
    public Notification get(int id) {
        return idExists(id) ? new Notification(context, getOptions(id)) : null;
    }

    /**
     * Set the badge number of the app icon.
     *
     * @param badge The badge number.
     */
    public void setBadge(int badge) {
        if (badge == 0) {
            new BadgeImpl(context).clearBadge();
        } else {
            new BadgeImpl(context).setBadge(badge);
        }
    }

    /**
     * Get all active status bar notifications.
     */
    StatusBarNotification[] getActiveNotifications() {
        if (SDK_INT >= M) {
            return getNotMgr().getActiveNotifications();
        } else {
            return new StatusBarNotification[0];
        }
    }

    /**
     * Shared private preferences for the application.
     */
    private SharedPreferences getPrefs() {
        return context.getSharedPreferences(PREF_KEY_ID, Context.MODE_PRIVATE);
    }

    /**
     * Notification manager for the application.
     */
    private NotificationManager getNotMgr() {
        return (NotificationManager) context.getSystemService(
                Context.NOTIFICATION_SERVICE);
    }

    /**
     * Notification compat manager for the application.
     */
    private NotificationManagerCompat getNotCompMgr() {
        return NotificationManagerCompat.from(context);
    }

}

// codebeat:enable[TOO_MANY_FUNCTIONS]
