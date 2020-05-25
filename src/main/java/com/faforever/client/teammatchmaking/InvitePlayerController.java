package com.faforever.client.teammatchmaking;

import com.faforever.client.fx.Controller;
import com.faforever.client.player.Player;
import com.faforever.client.player.PlayerService;
import com.jfoenix.controls.JFXListView;
import com.jfoenix.controls.JFXTextField;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.Pane;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.Collection;

@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@RequiredArgsConstructor
public class InvitePlayerController implements Controller<Pane> {

  private final PlayerService playerService;
  private final TeamMatchmakingService teamMatchmakingService;
  private final ObservableList<String> playerList = FXCollections.observableArrayList();
  private final FilteredList<String> filteredPlayerList = new FilteredList<>(playerList, p -> true);
  @FXML
  public Pane root;
  @FXML
  public JFXTextField playerTextField;
  @FXML
  public JFXListView<String> playersListView;

  @Override
  public void initialize() {
    playerTextField.textProperty().addListener((observable, oldValue, newValue) -> {
      playerList.setAll(getPlayerNames());
      playersListView.getSelectionModel().selectFirst();
    });

    filteredPlayerList.predicateProperty().bind(Bindings.createObjectBinding(() ->
            p -> p.toLowerCase().contains(playerTextField.getText().toLowerCase())
                && playerService.getCurrentPlayer().map(Player::getUsername).map(n -> !n.equals(p)).orElse(true),
        playerTextField.textProperty()
    ));

    playersListView.setItems(filteredPlayerList);
    playerTextField.setText("");
    playerTextField.requestFocus();
  }

  private Collection<String> getPlayerNames() {
    return playerService.getPlayerNames(); //TODO: filter for online players
  }

  @Override
  public Pane getRoot() {
    return root;
  }

  public void onInviteButtonClicked(ActionEvent event) {
    invite();
  }

  private void invite() {
    playersListView.getSelectionModel().getSelectedItems().forEach(teamMatchmakingService::invitePlayer);

    playerTextField.setText("");
  }

  public void onKeyPressed(KeyEvent keyEvent) {
    if (keyEvent.getCode() == KeyCode.ENTER) {
      invite();
    }
  }
}
