package com.faforever.client.replay;

import com.faforever.client.i18n.I18n;
import com.faforever.client.main.event.OpenOnlineReplayVaultEvent;
import com.faforever.client.main.event.ShowReplayEvent;
import com.faforever.client.notification.ImmediateNotification;
import com.faforever.client.notification.NotificationService;
import com.faforever.client.preferences.Preferences;
import com.faforever.client.preferences.PreferencesService;
import com.faforever.client.query.LogicalNodeController;
import com.faforever.client.query.SpecificationController;
import com.faforever.client.reporting.ReportingService;
import com.faforever.client.test.AbstractPlainJavaFxTest;
import com.faforever.client.theme.UiService;
import com.faforever.client.util.Tuple;
import com.faforever.client.vault.VaultEntityController.SearchType;
import com.faforever.client.vault.VaultEntityShowRoomController;
import com.faforever.client.vault.search.SearchController;
import com.faforever.client.vault.search.SearchController.SearchConfig;
import com.faforever.client.vault.search.SearchController.SortConfig;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.testfx.util.WaitForAsyncUtils;

import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class OnlineReplayVaultControllerTest extends AbstractPlainJavaFxTest {
  private static final int MAX_RESULTS = 100;

  private OnlineReplayVaultController instance;

  @Mock
  private ReplayService replayService;
  @Mock
  private UiService uiService;
  @Mock
  private ReplayDetailController replayDetailController;
  @Mock
  private SpecificationController specificationController;
  @Mock
  private LogicalNodeController logicalNodeController;
  @Mock
  private NotificationService notificationService;
  @Mock
  private I18n i18n;
  @Mock
  private SearchController searchController;
  @Mock
  private PreferencesService preferencesService;
  @Mock
  private ReportingService reportingService;
  @Mock
  private VaultEntityShowRoomController vaultEntityShowRoomController;

  @Captor
  private ArgumentCaptor<Consumer<SearchConfig>> searchListenerCaptor;
  private SortConfig sortOrder;
  private SearchConfig standardSearchConfig;
  private Replay testReplay = new Replay();

  @Before
  public void setUp() throws Exception {
    when(replayDetailController.getRoot()).thenReturn(new Pane());

    when(uiService.loadFxml("theme/vault/replay/replay_detail.fxml")).thenAnswer(invocation -> replayDetailController);

    when(replayService.getNewestReplaysWithPageCount(anyInt(), anyInt())).thenReturn(CompletableFuture.completedFuture(new Tuple<>(Collections.emptyList(), 0)));
    when(replayService.getHighestRatedReplaysWithPageCount(anyInt(), anyInt())).thenReturn(CompletableFuture.completedFuture(new Tuple<>(Collections.emptyList(), 0)));
    when(replayService.findById(anyInt())).thenReturn(CompletableFuture.completedFuture(Optional.of(testReplay)));
    when(replayService.getOwnReplaysWithPageCount(anyInt(),anyInt())).thenReturn(CompletableFuture.completedFuture(new Tuple<>(Collections.emptyList(), 0)));
    when(preferencesService.getPreferences()).thenReturn(new Preferences());
    when(uiService.loadFxml("theme/vault/vault_entity_show_room.fxml")).thenReturn(vaultEntityShowRoomController);
    when(vaultEntityShowRoomController.getRoot()).thenReturn(new VBox());
    when(vaultEntityShowRoomController.getLabel()).thenReturn(new Label());
    when(vaultEntityShowRoomController.getMoreButton()).thenReturn(new Button());
    when(vaultEntityShowRoomController.getPane()).thenReturn(new FlowPane());
    sortOrder = preferencesService.getPreferences().getVaultPrefs().getOnlineReplaySortConfig();
    standardSearchConfig = new SearchConfig(sortOrder, "query");

    instance = new OnlineReplayVaultController(replayService, uiService, notificationService, i18n, preferencesService, reportingService);

    loadFxml("theme/vault/vault_entity.fxml", clazz -> {
      if (SearchController.class.isAssignableFrom(clazz)) {
        return searchController;
      }
      if (SpecificationController.class.isAssignableFrom(clazz)) {
        return specificationController;
      }
      if (LogicalNodeController.class.isAssignableFrom(clazz)) {
        return logicalNodeController;
      }
      return instance;
    }, instance);

    verify(searchController).setSearchListener(searchListenerCaptor.capture());
  }

  @Test
  public void testSetSupplier() {
    instance.searchType = SearchType.SEARCH;
    instance.setSupplier(standardSearchConfig);
    instance.searchType = SearchType.NEWEST;
    instance.setSupplier(null);
    instance.searchType = SearchType.OWN;
    instance.setSupplier(null);
    instance.searchType = SearchType.HIGHEST_RATED;
    instance.setSupplier(null);
    instance.searchType = SearchType.PLAYER;
    instance.setSupplier(null);

    verify(replayService).findByQueryWithPageCount(standardSearchConfig.getSearchQuery(), instance.pageSize, 1, standardSearchConfig.getSortConfig());
    verify(replayService).getNewestReplaysWithPageCount(instance.pageSize, 1);
    verify(replayService).getHighestRatedReplaysWithPageCount(instance.pageSize, 1);
    verify(replayService).getOwnReplaysWithPageCount(instance.pageSize, 1);
    verify(replayService).getReplaysForPlayerWithPageCount(0, instance.pageSize, 1, standardSearchConfig.getSortConfig());
  }

  @Test
  public void testShowReplayEventWhenUninitialized() {
    Platform.runLater(() -> instance.display(new ShowReplayEvent(123)));
    WaitForAsyncUtils.waitForFxEvents();
    verify(replayDetailController).setReplay(testReplay);
  }

  @Test
  public void testShowReplayEventWhenInitialized() {
    Platform.runLater(() -> instance.display(new OpenOnlineReplayVaultEvent()));
    Platform.runLater(() -> instance.display(new ShowReplayEvent(123)));
    WaitForAsyncUtils.waitForFxEvents();
    verify(replayDetailController).setReplay(testReplay);
  }

  @Test
  public void showReplayButReplayNotPresent() {
    when(replayService.findById(anyInt())).thenReturn(CompletableFuture.completedFuture(Optional.empty()));
    Platform.runLater(() -> instance.display(new ShowReplayEvent(123)));
    WaitForAsyncUtils.waitForFxEvents();
    verify(notificationService).addNotification(any(ImmediateNotification.class));
  }
}
