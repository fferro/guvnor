/*
 * Copyright 2012 JBoss Inc
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

package org.kie.guvnor.projecteditor.client.forms;

import com.google.gwt.user.client.Command;
import com.google.gwt.user.client.ui.HasText;
import com.google.gwt.user.client.ui.IsWidget;
import com.google.gwt.user.client.ui.Widget;
import org.jboss.errai.bus.client.api.RemoteCallback;
import org.jboss.errai.ioc.client.api.Caller;
import org.kie.guvnor.projecteditor.model.GroupArtifactVersionModel;
import org.kie.guvnor.projecteditor.service.ProjectEditorService;
import org.uberfire.backend.vfs.Path;

import javax.inject.Inject;

public class GroupArtifactVersionEditorPanel
        implements GroupArtifactVersionEditorPanelView.Presenter, IsWidget {

    private final Caller<ProjectEditorService> projectEditorServiceCaller;
    private final GroupArtifactVersionEditorPanelView view;
    private Path path;
    private GroupArtifactVersionModel model;
    private HasText title;

    @Inject
    public GroupArtifactVersionEditorPanel(Caller<ProjectEditorService> projectEditorServiceCaller,
                                           GroupArtifactVersionEditorPanelView view) {
        this.projectEditorServiceCaller = projectEditorServiceCaller;
        this.view = view;
        view.setPresenter(this);
    }

    public void init(Path path, final HasText title) {
        this.path = path;
        this.title = title;
        projectEditorServiceCaller.call(
                new RemoteCallback<GroupArtifactVersionModel>() {
                    @Override
                    public void callback(GroupArtifactVersionModel gav) {
                        GroupArtifactVersionEditorPanel.this.model = gav;
                        view.setGroupId(gav.getGroupId());
                        view.setArtifactId(gav.getArtifactId());
                        title.setText(gav.getArtifactId());
                        view.setVersionId(gav.getVersion());
                    }
                }
        ).loadGav(path);
    }

    @Override
    public void onGroupIdChange(String groupId) {
        model.setGroupId(groupId);
    }

    @Override
    public void onArtifactIdChange(String artifactId) {
        model.setArtifactId(artifactId);
        title.setText(artifactId);
    }

    @Override
    public void onVersionIdChange(String versionId) {
        model.setVersion(versionId);
    }

    public void save(final Command callback) {
        projectEditorServiceCaller.call(
                new RemoteCallback<Object>() {
                    @Override
                    public void callback(Object o) {
                        callback.execute();
                        view.showSaveSuccessful("pom.xml");
                    }
                }
        ).saveGav(path, model);
    }

    @Override
    public Widget asWidget() {
        Widget widget = view.asWidget();
        return widget;
    }
}
