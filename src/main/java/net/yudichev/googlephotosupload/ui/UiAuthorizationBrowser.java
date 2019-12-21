package net.yudichev.googlephotosupload.ui;

import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;
import net.yudichev.jiotty.common.inject.BaseLifecycleComponent;
import net.yudichev.jiotty.connector.google.common.AuthorizationBrowser;

import javax.inject.Inject;
import javax.inject.Provider;

import static com.google.common.base.Preconditions.checkNotNull;
import static javafx.application.Platform.runLater;
import static net.yudichev.googlephotosupload.ui.Bindings.Primary;

final class UiAuthorizationBrowser extends BaseLifecycleComponent implements AuthorizationBrowser {
    static {
        System.setProperty("sun.net.http.allowRestrictedHeaders", "true");
    }

    private final Provider<Stage> primaryStageProvider;
    private final MainScreenController mainScreenController;
    private final FxmlContainerFactory fxmlContainerFactory;
    private Stage dialog;
    private FxmlContainer loginDialogFxmlContainer;

    @Inject
    UiAuthorizationBrowser(@Primary Provider<Stage> primaryStageProvider,
                           MainScreenController mainScreenController,
                           FxmlContainerFactory fxmlContainerFactory) {
        this.primaryStageProvider = checkNotNull(primaryStageProvider);
        this.mainScreenController = checkNotNull(mainScreenController);
        this.fxmlContainerFactory = checkNotNull(fxmlContainerFactory);
    }

    @Override
    public void browse(String url) {
        runLater(() -> {
            if (loginDialogFxmlContainer == null) {
                loginDialogFxmlContainer = fxmlContainerFactory.create("LoginDialog.fxml");
            }

            Stage primaryStage = primaryStageProvider.get();

            dialog = new Stage();
            dialog.setScene(new Scene(loginDialogFxmlContainer.root()));
            dialog.initOwner(primaryStage);
            dialog.initModality(Modality.APPLICATION_MODAL);
            dialog.show();

            LoginDialogFxController loginDialogFxController = loginDialogFxmlContainer.controller();
            loginDialogFxController.load(url);
        });
    }

    @Override
    protected void doStart() {
        runLater(() -> {
            closeDialog();
            mainScreenController.toFolderSelectionMode();
        });
    }

    @Override
    protected void doStop() {
        runLater(this::closeDialog);
    }

    private void closeDialog() {
        if (dialog != null) {
            dialog.close();
            dialog = null;
        }
    }
}
