/*
 * Copyright 2014 JBoss Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.guvnor.client.perspectives;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Event;
import javax.inject.Inject;

import com.google.gwt.event.logical.shared.CloseEvent;
import com.google.gwt.event.logical.shared.CloseHandler;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.PopupPanel;
import org.guvnor.client.resources.i18n.AppConstants;
import org.guvnor.m2repo.client.event.M2RepoRefreshEvent;
import org.guvnor.m2repo.client.event.M2RepoSearchEvent;
import org.guvnor.m2repo.client.upload.UploadForm;
import org.jboss.errai.ioc.client.container.SyncBeanManager;
import org.uberfire.client.annotations.Perspective;
import org.uberfire.client.annotations.WorkbenchMenu;
import org.uberfire.client.annotations.WorkbenchPanel;
import org.uberfire.client.annotations.WorkbenchPerspective;
import org.uberfire.client.mvp.PlaceManager;
import org.uberfire.client.util.Layouts;
import org.uberfire.lifecycle.OnStartup;
import org.uberfire.mvp.Command;
import org.uberfire.mvp.impl.DefaultPlaceRequest;
import org.uberfire.security.annotations.Roles;
import org.uberfire.workbench.model.PerspectiveDefinition;
import org.uberfire.workbench.model.impl.PartDefinitionImpl;
import org.uberfire.workbench.model.impl.PerspectiveDefinitionImpl;
import org.uberfire.workbench.model.menu.MenuFactory;
import org.uberfire.workbench.model.menu.Menus;
import org.uberfire.client.workbench.panels.impl.StaticWorkbenchPanelPresenter;

/**
 * A Perspective to show M2_REPO related screen
 */
@Roles({ "admin" })
@ApplicationScoped
@WorkbenchPerspective(identifier = "org.guvnor.m2repo.client.perspectives.GuvnorM2RepoPerspective")
public class M2RepoPerspective extends FlowPanel {

    @Inject
    private Event<M2RepoSearchEvent> searchEvents;

    @Inject
    private Event<M2RepoRefreshEvent> refreshEvents;

    @Inject
    private PlaceManager placeManager;

    @Inject
    private SyncBeanManager iocManager;

    private PerspectiveDefinition perspective;

    @Inject
    @WorkbenchPanel(parts = "M2RepoEditor")
    FlowPanel m2RepoEditor;

    @PostConstruct
    private void init() {
        Layouts.setToFillParent( m2RepoEditor );
        add(m2RepoEditor);
    }

    private void buildPerspective() {
        this.perspective = new PerspectiveDefinitionImpl( StaticWorkbenchPanelPresenter.class.getName() );
        this.perspective.setName( "M2 Repository Explorer" );

        this.perspective.getRoot().addPart( new PartDefinitionImpl( new DefaultPlaceRequest( "M2RepoEditor" ) ) );
    }

    @WorkbenchMenu
    public Menus getMenus() {
        return MenuFactory.newTopLevelMenu( AppConstants.INSTANCE.Upload() )
                .respondsWith( new Command() {
                    @Override
                    public void execute() {
                        final UploadForm uploadForm = iocManager.lookupBean( UploadForm.class ).getInstance();
                        //When pop-up is closed destroy bean to avoid memory leak
                        uploadForm.addCloseHandler( new CloseHandler<PopupPanel>() {

                            @Override
                            public void onClose( CloseEvent<PopupPanel> event ) {
                                iocManager.destroyBean( uploadForm );
                            }

                        } );
                        uploadForm.show();
                    }
                } )
                .endMenu()
                .newTopLevelMenu( AppConstants.INSTANCE.Refresh() )
                .respondsWith( new Command() {
                    @Override
                    public void execute() {
                        refreshEvents.fire( new M2RepoRefreshEvent() );
                    }
                } )
                .endMenu()
                .build();
    }

    @OnStartup
    public void onStartup() {

    }
}
