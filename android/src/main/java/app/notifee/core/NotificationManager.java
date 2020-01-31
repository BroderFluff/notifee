package app.notifee.core;

import android.app.Notification;
import android.app.PendingIntent;
import android.graphics.Bitmap;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.concurrent.futures.ResolvableFuture;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.app.RemoteInput;
import androidx.core.graphics.drawable.IconCompat;
import androidx.work.Data;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.ListenableWorker;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.google.android.gms.tasks.Continuation;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;

import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import app.notifee.core.bundles.NotificationAndroidActionBundle;
import app.notifee.core.bundles.NotificationAndroidBundle;
import app.notifee.core.bundles.NotificationAndroidStyleBundle;
import app.notifee.core.bundles.NotificationBundle;
import app.notifee.core.bundles.ScheduleBundle;
import app.notifee.core.events.NotificationEvent;
import app.notifee.core.utils.ResourceUtils;
import app.notifee.core.utils.TextUtils;

import static app.notifee.core.Notifee.CACHED_THREAD_POOL;
import static app.notifee.core.ReceiverService.ACTION_PRESS_INTENT;
import static app.notifee.core.Worker.WORK_TYPE_NOTIFICATION_SCHEDULE;

class NotificationManager {
  private static final String TAG = "NotificationManager";

  private static Task<NotificationCompat.Builder> notificationBundleToBuilder(
    NotificationBundle notificationBundle
  ) {
    final NotificationAndroidBundle androidBundle = notificationBundle.getAndroidBundle();

    /*
     * Construct the initial NotificationCompat.Builder instance
     */
    Callable<NotificationCompat.Builder> builderCallable = () -> {
      NotificationCompat.Builder builder = new NotificationCompat.Builder(
        ContextHolder.getApplicationContext(), androidBundle.getChannelId());

      // must always keep at top
      builder.setExtras(notificationBundle.getData());

      builder.setDeleteIntent(ReceiverService
        .createIntent(ReceiverService.DELETE_INTENT, new String[]{"notification"},
          notificationBundle.toBundle()
        ));

      builder.setContentIntent(ReceiverService
        .createIntent(
          ReceiverService.PRESS_INTENT,
          new String[]{"notification", "pressAction"},
          notificationBundle.toBundle(),
          androidBundle.getPressAction()
        ));

      if (notificationBundle.getTitle() != null) {
        builder.setContentTitle(TextUtils.fromHtml(notificationBundle.getTitle()));
      }

      if (notificationBundle.getSubTitle() != null) {
        builder.setSubText(TextUtils.fromHtml(notificationBundle.getSubTitle()));
      }

      if (notificationBundle.getBody() != null) {
        builder.setContentText(TextUtils.fromHtml(notificationBundle.getBody()));
      }

      if (androidBundle.getBadgeIconType() != null) {
        builder.setBadgeIconType(androidBundle.getBadgeIconType());
      }

      if (androidBundle.getCategory() != null) {
        builder.setCategory(androidBundle.getCategory());
      }

      if (androidBundle.getColor() != null) {
        builder.setColor(androidBundle.getColor());
      }

      builder.setColorized(androidBundle.getColorized());
      builder.setChronometerCountDown(androidBundle.getChronometerCountDown());
      builder.setDefaults(androidBundle.getDefaults());

      if (androidBundle.getGroup() != null) {
        builder.setGroup(androidBundle.getGroup());
      }

      builder.setGroupAlertBehavior(androidBundle.getGroupAlertBehaviour());
      builder.setGroupSummary(androidBundle.getGroupSummary());

      if (androidBundle.getInputHistory() != null) {
        builder.setRemoteInputHistory(androidBundle.getInputHistory());
      }

      if (androidBundle.getLights() != null) {
        ArrayList<Integer> lights = androidBundle.getLights();
        builder.setLights(lights.get(0), lights.get(1), lights.get(2));
      }

      builder.setLocalOnly(androidBundle.getLocalOnly());

      if (androidBundle.getNumber() != null) {
        builder.setNumber(androidBundle.getNumber());
      }

      builder.setOngoing(androidBundle.getOngoing());
      builder.setOnlyAlertOnce(androidBundle.getOnlyAlertOnce());
      builder.setPriority(androidBundle.getPriority());

      NotificationAndroidBundle.AndroidProgress progress = androidBundle.getProgress();
      if (progress != null) {
        builder.setProgress(progress.getMax(), progress.getCurrent(), progress.getIndeterminate());
      }

      if (androidBundle.getShortcutId() != null) {
        builder.setShortcutId(androidBundle.getShortcutId());
      }

      builder.setShowWhen(androidBundle.getShowTimestamp());

      Integer smallIconId = androidBundle.getSmallIcon();
      if (smallIconId != null) {
        Integer smallIconLevel = androidBundle.getSmallIconLevel();
        if (smallIconLevel != null) {
          builder.setSmallIcon(smallIconId, smallIconLevel);
        } else {
          builder.setSmallIcon(smallIconId);
        }
      }

      if (androidBundle.getSortKey() != null) {
        builder.setSortKey(androidBundle.getSortKey());
      }

      if (androidBundle.getTicker() != null) {
        builder.setTicker(androidBundle.getTicker());
      }

      if (androidBundle.getTimeoutAfter() != null) {
        builder.setTimeoutAfter(androidBundle.getTimeoutAfter());
      }

      builder.setUsesChronometer(androidBundle.getShowChronometer());

      long[] vibrationPattern = androidBundle.getVibrationPattern();
      if (vibrationPattern.length > 0) builder.setVibrate(vibrationPattern);

      builder.setVisibility(androidBundle.getVisibility());

      long timestamp = androidBundle.getTimestamp();
      if (timestamp > -1) builder.setWhen(timestamp);

      builder.setAutoCancel(androidBundle.getAutoCancel());

      return builder;
    };

    /*
     * A task continuation that fetches the largeIcon through Fresco, if specified.
     */
    Continuation<NotificationCompat.Builder, NotificationCompat.Builder> largeIconContinuation = task -> {
      NotificationCompat.Builder builder = task.getResult();

      if (androidBundle.hasLargeIcon()) {
        String largeIcon = androidBundle.getLargeIcon();
        Bitmap largeIconBitmap = null;

        try {
          largeIconBitmap = Tasks.await(
            ResourceUtils.getImageBitmapFromUrl(largeIcon),
            10, TimeUnit.SECONDS
          );
        } catch (TimeoutException e) {
          Logger.e(TAG, "Timeout occurred whilst trying to retrieve a largeIcon image: " + largeIcon, e);
        } catch (Exception e) {
          Logger.e(TAG, "An error occurred whilst trying to retrieve a largeIcon image: " + largeIcon, e);
        }

        if (largeIconBitmap != null) {
          builder.setLargeIcon(largeIconBitmap);
        }
      }

      return builder;
    };

    /*
     * A task continuation that builds all actions, if any. Additionally fetches icon bitmaps
     * through Fresco.
     */
    Continuation<NotificationCompat.Builder, NotificationCompat.Builder> actionsContinuation = task -> {
      NotificationCompat.Builder builder = task.getResult();
      ArrayList<NotificationAndroidActionBundle> actionBundles = androidBundle.getActions();

      if (actionBundles == null) {
        return builder;
      }

      for (NotificationAndroidActionBundle actionBundle : actionBundles) {
        PendingIntent pendingIntent = ReceiverService
          .createIntent(
            ACTION_PRESS_INTENT,
            new String[]{"notification", "pressAction"},
            notificationBundle.toBundle(),
            actionBundle.getPressAction().toBundle()
          );

        String icon = actionBundle.getIcon();
        Bitmap iconBitmap = null;

        if (icon != null) {
          try {
            iconBitmap = Tasks.await(
              ResourceUtils.getImageBitmapFromUrl(actionBundle.getIcon()),
              10, TimeUnit.SECONDS
            );
          } catch (TimeoutException e) {
            Logger.e(TAG, "Timeout occurred whilst trying to retrieve an action icon: " + icon, e);
          } catch (Exception e) {
            Logger.e(TAG, "An error occurred whilst trying to retrieve an action icon: " + icon, e);
          }
        }

        IconCompat iconCompat = null;
        if (iconBitmap != null) {
          iconCompat = IconCompat.createWithAdaptiveBitmap(iconBitmap);
        }

        NotificationCompat.Action.Builder actionBuilder = new NotificationCompat.Action.Builder(
          iconCompat,
          TextUtils.fromHtml(actionBundle.getTitle()),
          pendingIntent
        );

        RemoteInput remoteInput = actionBundle.getRemoteInput(actionBuilder);
        if (remoteInput != null) {
          actionBuilder.addRemoteInput(remoteInput);
        }

        builder.addAction(actionBuilder.build());
      }

      return builder;
    };

    /*
     * A task continuation that builds the notification style, if any. Additionally fetches any
     * image bitmaps (e.g. Person image, or BigPicture image) through Fresco.
     */
    Continuation<NotificationCompat.Builder, NotificationCompat.Builder> styleContinuation = task -> {
      NotificationCompat.Builder builder = task.getResult();
      NotificationAndroidStyleBundle androidStyleBundle = androidBundle.getStyle();
      if (androidStyleBundle == null) {
        return builder;
      }

      Task<NotificationCompat.Style> styleTask = androidStyleBundle.getStyleTask(
        CACHED_THREAD_POOL
      );
      if (styleTask == null) {
        return builder;
      }

      NotificationCompat.Style style = Tasks.await(styleTask);
      if (style != null) {
        builder.setStyle(style);
      }

      return builder;
    };

    return Tasks.call(CACHED_THREAD_POOL, builderCallable)
      // get a large image bitmap if largeIcon is set
      .continueWith(CACHED_THREAD_POOL, largeIconContinuation)
      // build notification actions, tasks based to allow image fetching
      .continueWith(CACHED_THREAD_POOL, actionsContinuation)
      // build notification style, tasks based to allow image fetching
      .continueWith(CACHED_THREAD_POOL, styleContinuation);
  }

  static Task<Void> cancelNotification(@NonNull String notificationId) {
    return Tasks.call(() -> {
      NotificationManagerCompat notificationManagerCompat = NotificationManagerCompat
        .from(ContextHolder.getApplicationContext());
      notificationManagerCompat.cancel(notificationId.hashCode());

      WorkManager.getInstance(ContextHolder.getApplicationContext())
        .cancelUniqueWork("schedule:" + notificationId);

      return null;
    });
  }

  static Task<Void> cancelAllNotifications() {
    return Tasks.call(() -> {
      NotificationManagerCompat notificationManagerCompat = NotificationManagerCompat
        .from(ContextHolder.getApplicationContext());
      notificationManagerCompat.cancelAll();

      WorkManager.getInstance(ContextHolder.getApplicationContext())
        .cancelAllWorkByTag(WORK_TYPE_NOTIFICATION_SCHEDULE);

      return null;
    });
  }


  static Task<Void> displayNotification(NotificationBundle notificationBundle) {
    return notificationBundleToBuilder(notificationBundle)
      .continueWith(CACHED_THREAD_POOL, (task) -> {
        NotificationCompat.Builder builder = task.getResult();
        NotificationAndroidBundle androidBundle = notificationBundle.getAndroidBundle();
        Notification notification = Objects.requireNonNull(builder).build();
        int hashCode = notificationBundle.getHashCode();

        if (androidBundle.getAsForegroundService()) {
          ForegroundService.start(hashCode, notification, notificationBundle.toBundle());
        } else {
          NotificationManagerCompat.from(ContextHolder.getApplicationContext())
            .notify(hashCode, notification);
        }

        EventBus.post(new NotificationEvent(NotificationEvent.TYPE_DELIVERED, notificationBundle));

        return null;
      });
  }

  static Task<Void> scheduleNotification(Bundle notificationBundle, Bundle scheduleBundle) {
    return Tasks.call(CACHED_THREAD_POOL, () -> {
      NotificationBundle notification = NotificationBundle.fromBundle(notificationBundle);
      ScheduleBundle schedule = ScheduleBundle.fromBundle(scheduleBundle);

      Data workData = new Data.Builder()
        .putString(Worker.KEY_WORK_TYPE, WORK_TYPE_NOTIFICATION_SCHEDULE)
//        .putInt(schedule.)
//        .putByteArray("notification", notificationBundle) // todo send serialized bundle
        .build();

      int timestamp = schedule.getTimestamp();
      int interval = schedule.getInterval();
      long delay = 0;


      if (timestamp != -1) {
        delay = Math.round((timestamp - System.currentTimeMillis()) / 1000);
      }

      // One time scheduled
      if (interval == -1) {
        OneTimeWorkRequest.Builder workRequestBuilder = new OneTimeWorkRequest.Builder(Worker.class);
        workRequestBuilder.addTag(WORK_TYPE_NOTIFICATION_SCHEDULE);
        workRequestBuilder.setInputData(workData);
        workRequestBuilder.setInitialDelay(delay, TimeUnit.SECONDS);

        WorkManager.getInstance(ContextHolder.getApplicationContext())
          .enqueueUniqueWork("schedule:" + notification.getId(), ExistingWorkPolicy.REPLACE, workRequestBuilder.build());
      } else {
        PeriodicWorkRequest.Builder workRequestBuilder = new PeriodicWorkRequest.Builder(Worker.class, interval, TimeUnit.MINUTES);
        workRequestBuilder.addTag(WORK_TYPE_NOTIFICATION_SCHEDULE);
        workRequestBuilder.setInputData(workData);
        workRequestBuilder.setInitialDelay(delay, TimeUnit.SECONDS);

        WorkManager.getInstance(ContextHolder.getApplicationContext())
          .enqueueUniquePeriodicWork("schedule:" + notification.getId(), ExistingPeriodicWorkPolicy.REPLACE, workRequestBuilder.build());
      }

      EventBus.post(new NotificationEvent(NotificationEvent.TYPE_SCHEDULED, notification));

      return null;
    });
  }

  static void doScheduledWork(Data workData, final ResolvableFuture<ListenableWorker.Result> completer) {
    String notificationString = workData.getString("notification");
    String scheduleString = workData.getString("schedule");

    if (notificationString == null || scheduleString == null) {
      Logger.w(TAG, "Attempted to handle doScheduledWork but no notification or schedule data was found.");
      completer.set(ListenableWorker.Result.success());
      return;
    }

    NotificationBundle notificationBundle = NotificationBundle.fromJSONString(notificationString);
    NotificationManager.displayNotification(notificationBundle).addOnCompleteListener(task -> {
      completer.set(ListenableWorker.Result.success());
      if (!task.isSuccessful()) {
        Logger.e(TAG, "Failed to display scheduled notification", task.getException());
      }
    });

  }
}
