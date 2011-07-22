package org.drools.guvnor.client.explorer.navigation.qa;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.shared.EventBus;
import org.drools.guvnor.client.common.GenericCallback;
import org.drools.guvnor.client.explorer.AcceptTabItem;
import org.drools.guvnor.client.explorer.ClientFactory;
import org.drools.guvnor.client.messages.Constants;
import org.drools.guvnor.client.qa.AnalysisView;
import org.drools.guvnor.client.rpc.PackageConfigData;
import org.drools.guvnor.client.util.Activity;

public class VerifierActivity extends Activity {

    private Constants constants = GWT.create( Constants.class );

    private final String moduleUuid;
    private final ClientFactory clientFactory;

    public VerifierActivity(String moduleUuid,
                            ClientFactory clientFactory) {
        this.moduleUuid = moduleUuid;
        this.clientFactory = clientFactory;
    }

    @Override
    public void start(AcceptTabItem tabbedPanel, EventBus eventBus) {
        openVerifierView( tabbedPanel );
    }

    public void openVerifierView(final AcceptTabItem tabbedPanel) {

        clientFactory.getPackageService().loadPackageConfig(
                moduleUuid,
                new GenericCallback<PackageConfigData>() {
                    public void onSuccess(PackageConfigData packageConfigData) {
                        tabbedPanel.addTab(
                                constants.AnalysisForPackage( packageConfigData.getName() ),
                                new AnalysisView(
                                        packageConfigData.getUuid(),
                                        packageConfigData.getName() ) );
                    }
                } );

    }

}