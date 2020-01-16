package io.invertase.notifee;

import android.os.Bundle;

import androidx.annotation.Keep;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.WritableMap;

import app.notifee.core.EventListener;
import app.notifee.core.events.BlockStateEvent;
import app.notifee.core.events.LogEvent;
import app.notifee.core.events.NotificationEvent;

import static io.invertase.notifee.NotifeeReactUtils.isAppInForeground;

@Keep
public class NotifeeEventSubscriber implements EventListener {
  static final String NOTIFICATION_EVENT_KEY = "app.notifee.notification.event";
  static final String FOREGROUND_NOTIFICATION_TASK_KEY = "app.notifee.foreground.task";

  private static final String KEY_TYPE = "type";
  private static final String KEY_DETAIL = "detail";
  private static final String KEY_HEADLESS = "headless";
  private static final String KEY_NOTIFICATION = "notification";

  @Override
  public void onNotificationEvent(NotificationEvent notificationEvent) {
    WritableMap eventMap = Arguments.createMap();
    WritableMap eventDetailMap = Arguments.createMap();
    eventMap.putInt(KEY_TYPE, notificationEvent.getType());

    // TODO `action` bundle if applicable?
    // TODO `input` if applicable?

    eventDetailMap.putMap(KEY_NOTIFICATION,
      Arguments.fromBundle(notificationEvent.getNotification().toBundle())
    );

    eventMap.putMap(KEY_DETAIL, eventDetailMap);

    if (isAppInForeground()) {
      eventMap.putBoolean(KEY_HEADLESS, false);
      NotifeeReactUtils.sendEvent(NOTIFICATION_EVENT_KEY, eventMap);
    } else {
      eventMap.putBoolean(KEY_HEADLESS, true);
      NotifeeReactUtils.startHeadlessTask(NOTIFICATION_EVENT_KEY, eventMap, 60000, null);
    }
  }

  @Override
  public void onLogEvent(LogEvent logEvent) {
    // TODO
  }

  @Override
  public void onBlockStateEvent(BlockStateEvent blockStateEvent) {
    WritableMap eventMap = Arguments.createMap();
    WritableMap eventDetailMap = Arguments.createMap();

    eventMap.putInt(KEY_TYPE, blockStateEvent.getType());

    int type = blockStateEvent.getType();

    if (type == BlockStateEvent.TYPE_CHANNEL_BLOCKED ||
      type == BlockStateEvent.TYPE_CHANNEL_GROUP_BLOCKED) {
      String mapKey = type == BlockStateEvent.TYPE_CHANNEL_BLOCKED ? "channel" : "channelGroup";
      Bundle channelOrGroupBundle = blockStateEvent.getChannelOrGroupBundle();
      if (channelOrGroupBundle != null) {
        eventDetailMap
          .putMap(mapKey, Arguments.fromBundle(blockStateEvent.getChannelOrGroupBundle()));
      }
    }

    if (type == BlockStateEvent.TYPE_APP_BLOCKED) {
      eventDetailMap.putBoolean("blocked", blockStateEvent.isBlocked());
    }

    eventMap.putMap(KEY_DETAIL, eventDetailMap);

    if (isAppInForeground()) {
      eventMap.putBoolean(KEY_HEADLESS, false);
      NotifeeReactUtils.sendEvent(NOTIFICATION_EVENT_KEY, eventMap);
    } else {
      eventMap.putBoolean(KEY_HEADLESS, true);
      NotifeeReactUtils.startHeadlessTask(NOTIFICATION_EVENT_KEY, eventMap, 0,
        blockStateEvent::setCompletionResult
      );
    }
  }
}
