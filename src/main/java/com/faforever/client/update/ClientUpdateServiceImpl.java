package com.faforever.client.update;

import com.faforever.client.FafClientApplication;
import com.faforever.client.fx.PlatformService;
import com.faforever.client.i18n.I18n;
import com.faforever.client.notification.Action;
import com.faforever.client.notification.NotificationService;
import com.faforever.client.notification.PersistentNotification;
import com.faforever.client.preferences.PreferencesService;
import com.faforever.client.task.TaskService;
import com.faforever.client.user.event.LoggedInEvent;
import com.google.common.annotations.VisibleForTesting;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

import static com.faforever.client.notification.Severity.INFO;
import static com.faforever.client.notification.Severity.WARN;
import static com.faforever.commons.io.Bytes.formatSize;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.apache.commons.lang3.StringUtils.defaultString;


@Lazy
@Service
@Slf4j
@Profile("!" + FafClientApplication.PROFILE_OFFLINE)
public class ClientUpdateServiceImpl implements ClientUpdateService {

  private static final String DEVELOPMENT_VERSION_STRING = "dev";

  private final TaskService taskService;
  private final NotificationService notificationService;
  private final I18n i18n;
  private final PlatformService platformService;
  private final ApplicationContext applicationContext;
  private final PreferencesService preferencesService;

  @VisibleForTesting
  String currentVersion;

  public ClientUpdateServiceImpl(
      TaskService taskService,
      NotificationService notificationService,
      I18n i18n,
      PlatformService platformService,
      ApplicationContext applicationContext,
      PreferencesService preferencesService) {
    this.taskService = taskService;
    this.notificationService = notificationService;
    this.i18n = i18n;
    this.platformService = platformService;
    this.applicationContext = applicationContext;
    this.preferencesService = preferencesService;

    currentVersion = defaultString(Version.getCurrentVersion(), DEVELOPMENT_VERSION_STRING);
    log.info("Current version: {}", currentVersion);
  }

  @Override
  public CompletableFuture<UpdateInfo> checkForUpdateInBackground() {
    CompletableFuture<UpdateInfo> task;
    if (preferencesService.getPreferences().isPrereleaseCheckEnabled()) {
      task = taskService.submitTask(applicationContext.getBean(CheckForBetaUpdateTask.class)).getFuture();
    } else {
      task = taskService.submitTask(applicationContext.getBean(CheckForReleaseUpdateTask.class)).getFuture();
    }

    return task.thenApply(updateInfo -> {
      onUpdateInfo(updateInfo);
      return updateInfo;
    }).exceptionally(throwable -> {
      log.warn("Client update check failed", throwable);
      return null;
    });
  }

  @EventListener(classes = LoggedInEvent.class)
  public void onLoggedInEvent() {
    checkForUpdateInBackground();
  }

  private void onUpdateInfo(UpdateInfo updateInfo) {
    if (updateInfo == null) {
      return;
    }

    if (!Version.shouldUpdate(getCurrentVersion(), updateInfo.getName())) {
      return;
    }

    if (preferencesService.getPreferences().isAutoUpdate()) {
      updateInBackground(updateInfo);
      return;
    }

    notificationService.addNotification(new PersistentNotification(
        i18n.get(updateInfo.isPrerelease() ? "clientUpdateAvailable.prereleaseNotification" : "clientUpdateAvailable.notification", updateInfo.getName(), formatSize(updateInfo.getSize(), i18n.getUserSpecificLocale())),
        INFO, asList(
        new Action(i18n.get("clientUpdateAvailable.downloadAndInstall"), event -> updateInBackground(updateInfo)),
        new Action(i18n.get("clientUpdateAvailable.releaseNotes"), Action.Type.OK_STAY,
            event -> platformService.showDocument(updateInfo.getReleaseNotesUrl().toExternalForm())
        )))
    );
  }

  @Override
  public String getCurrentVersion() {
    return currentVersion;
  }

  public ClientUpdateTask updateInBackground(UpdateInfo updateInfo) {
    ClientUpdateTask task = applicationContext.getBean(ClientUpdateTask.class);
    task.setUpdateInfo(updateInfo);

    taskService.submitTask(task).getFuture()
        .exceptionally(throwable -> {
          log.warn("Update failed", throwable);
          notificationService.addNotification(
              new PersistentNotification(i18n.get("clientUpdateDownloadFailed.notification"), WARN, singletonList(
                  new Action(i18n.get("clientUpdateDownloadFailed.retry"), event -> updateInBackground(updateInfo))
              ))
          );
          return null;
        });

    return task;
  }
}
