package org.guvnor.asset.management.backend.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Event;
import javax.inject.Inject;
import javax.inject.Named;

import org.guvnor.asset.management.model.ProjectStructureModel;
import org.guvnor.asset.management.service.ProjectStructureService;
import org.guvnor.common.services.backend.exceptions.ExceptionUtilities;
import org.guvnor.common.services.project.model.GAV;
import org.guvnor.common.services.project.model.POM;
import org.guvnor.common.services.project.model.Project;
import org.guvnor.common.services.project.service.POMService;
import org.guvnor.common.services.project.service.ProjectService;
import org.guvnor.common.services.shared.metadata.MetadataService;
import org.guvnor.m2repo.backend.server.GuvnorM2Repository;
import org.guvnor.structure.repositories.Repository;
import org.guvnor.common.services.backend.util.CommentedOptionFactory;
import org.guvnor.structure.repositories.RepositoryService;
import org.guvnor.structure.repositories.RepositoryUpdatedEvent;
import org.jboss.errai.bus.server.annotations.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.uberfire.backend.server.util.Paths;
import org.uberfire.backend.vfs.Path;
import org.uberfire.commons.validation.PortablePreconditions;
import org.uberfire.io.IOService;
import org.uberfire.java.nio.file.DirectoryStream;
import org.uberfire.java.nio.file.FileSystem;
import org.uberfire.java.nio.file.Files;

import static org.guvnor.structure.backend.repositories.EnvironmentParameters.*;

@Service
@ApplicationScoped
public class ProjectStructureServiceImpl
        implements ProjectStructureService {

    private static final Logger logger = LoggerFactory.getLogger( ProjectStructureServiceImpl.class );

    @Inject
    private POMService pomService;

    @Inject
    private ProjectService<? extends Project> projectService;

    @Inject
    private RepositoryService repositoryService;

    @Inject
    private MetadataService metadataService;

//    @Inject
//    private ValidationService validationService;

    @Inject
    private GuvnorM2Repository m2service;

    @Inject
    private CommentedOptionFactory optionsFactory;

    @Inject
    private Event<RepositoryUpdatedEvent> repositoryUpdatedEvent;

    @Inject
    @Named( "ioStrategy" )
    private IOService ioService;

    public Path initProjectStructure( GAV gav, Repository repo ) {

        POM pom = new POM( repo.getAlias(), repo.getAlias(), gav
                , true );
        //Creating the parent pom
        final Path fsRoot = repo.getRoot();
        final Path pathToPom = pomService.create( fsRoot,
                "",
                pom );
        //Deploying the parent pom artifact,
        // it needs to be deployed before the first child is created
        m2service.deployParentPom( gav );

        updateManagedStatus( repo, true );

        return pathToPom;
    }

    @Override
    public Repository initRepository( final Repository repo, boolean managed ) {
        return updateManagedStatus( repo, managed );
    }

    private Repository updateManagedStatus( final Repository repo, final boolean managed) {
        Map<String, Object> config = new HashMap<String, Object>( );

        config.put( MANAGED, managed );
        Repository updatedRepo = repositoryService.updateRepository( repo, config );
        repositoryUpdatedEvent.fire( new RepositoryUpdatedEvent( repo, updatedRepo ) );

        return updatedRepo;
    }

    @Override
    public Path convertToMultiProjectStructure( final List<Project> projects,
            final GAV parentGav,
            final Repository repo,
            final boolean updateChildrenGav,
            final String comment ) {

        if ( projects == null || parentGav == null || repo == null ) {
            return null;
        }

        try {
            POM parentPom;
            Path path = initProjectStructure( parentGav, repo );

            parentPom = pomService.load( path );
            if ( parentPom == null ) {
                //uncommon case, the pom was just created.
                return null;
            }

            ioService.startBatch( new FileSystem[] { Paths.convert( path ).getFileSystem() }, optionsFactory.makeCommentedOption( comment != null ? comment : "" ) );

            POM pom;
            boolean saveParentPom = false;
            for ( Project project : projects ) {
                pom = pomService.load( project.getPomXMLPath() );
                pom.setParent( parentGav );
                if ( updateChildrenGav ) {
                    pom.getGav().setGroupId( parentGav.getGroupId() );
                    pom.getGav().setVersion( parentGav.getVersion() );
                }
                pomService.save( project.getPomXMLPath(), pom, null, comment );

                parentPom.setMultiModule( true );
                parentPom.getModules().add( pom.getName() != null ? pom.getName() : pom.getGav().getArtifactId() );
                saveParentPom = true;
            }

            if ( saveParentPom ) {
                pomService.save( path, parentPom, null, comment );
            }

            return path;
        } catch ( Exception e ) {
            throw ExceptionUtilities.handleException( e );
        } finally {
            ioService.endBatch();
        }
    }

    @Override
    public ProjectStructureModel load( final Repository repository ) {
        return load( repository, true );
    }

    @Override
    public ProjectStructureModel load( final Repository repository, boolean includeModules ) {

        if ( repository == null ) return null;
        Repository _repository = repositoryService.getRepository( repository.getAlias() );

        if ( _repository == null ) return null;

        ProjectStructureModel model = new ProjectStructureModel();
        Boolean managedStatus = _repository.getEnvironment() != null ? (Boolean)_repository.getEnvironment().get( MANAGED ) : null;
        if ( managedStatus != null) {
            model.setManaged( managedStatus );
        }

        Path path = repository.getRoot();
        final Project project = projectService.resolveToParentProject( path );

        if ( project != null ) {
            if ( !model.isManaged() ) {
                //uncommon case, the repository is managed. Update managed status.
                updateManagedStatus( _repository, true );
                model.setManaged( true );
            }
            model.setPOM( pomService.load( project.getPomXMLPath() ) );
            model.setPOMMetaData( metadataService.getMetadata( project.getPomXMLPath() ) );
            model.setPathToPOM( project.getPomXMLPath() );
            model.setModules( new ArrayList<String>( project.getModules() ) );
            if ( includeModules && project.getModules() != null ) {
                org.uberfire.java.nio.file.Path parentPath = Paths.convert( project.getRootPath() );
                Project moduleProject;
                for ( String module : project.getModules() ) {
                    moduleProject = projectService.resolveProject( Paths.convert( parentPath.resolve( module ) ) );
                    model.getModulesProject().put( module, moduleProject );
                }
            }

        } else {
            //if no parent pom.xml present we must check if there are orphan projects for this repository.
            List<Project> repositoryProjects = getProjects( repository );
            if ( !repositoryProjects.isEmpty() ) {
                model.setOrphanProjects( repositoryProjects );
                POM pom;
                for ( Project orphanProject : repositoryProjects ) {
                    pom = pomService.load( orphanProject.getPomXMLPath() );
                    model.getOrphanProjectsPOM().put( orphanProject.getSignatureId(), pom );
                }
                if ( managedStatus == null && repositoryProjects.size() > 1 ) {
                    //update managed status
                    updateManagedStatus( _repository, false );
                }

            } else if ( managedStatus == null) {
                //there are no projects and the managed attribute is not set, means the repository was never initialized.
                model = null;
            }
        }

        return model;
    }

    @Override
    public void save( final Path pathToPomXML,
            final ProjectStructureModel model,
            final String comment ) {
        final FileSystem fs = Paths.convert( pathToPomXML ).getFileSystem();
        try {
            pomService.save( pathToPomXML,
                    model.getPOM(),
                    model.getPOMMetaData(),
                    comment,
                    true );

        } catch ( final Exception e ) {
            throw ExceptionUtilities.handleException( e );
        }
    }

    @Override
    public boolean validate( final POM pom ) {
        PortablePreconditions.checkNotNull( "pom",
                pom );
        final String name = pom.getName();
        final String groupId = pom.getGav().getGroupId();
        final String artifactId = pom.getGav().getArtifactId();
        final String version = pom.getGav().getVersion();

        final String[] groupIdComponents = ( groupId == null ? new String[] { } : groupId.split( "\\.",
                -1 ) );
        final String[] artifactIdComponents = ( artifactId == null ? new String[] { } : artifactId.split( "\\.",
                -1 ) );

//        final boolean validName = !( name == null || name.isEmpty() ) && validationService.isProjectNameValid( name );
//        final boolean validGroupId = !( groupIdComponents.length == 0 || validationService.evaluateIdentifiers( groupIdComponents ).containsValue( Boolean.FALSE ) );
//        final boolean validArtifactId = !( artifactIdComponents.length == 0 || validationService.evaluateArtifactIdentifiers( artifactIdComponents ).containsValue( Boolean.FALSE ) );
//        final boolean validVersion = !( version == null || version.isEmpty() || !version.matches( "^[a-zA-Z0-9\\.\\-_]+$" ) );

//        return validName && validGroupId && validArtifactId && validVersion;
        return true;
    }


    @Override
    public void delete( final Path pathToPomXML, final String comment ) {
        projectService.delete( pathToPomXML, comment );
    }

    private List<Project> getProjects( final Repository repository ) {
        final List<Project> repositoryProjects = new ArrayList<Project>();
        if ( repository == null ) {
            return repositoryProjects;
        }
        final Path repositoryRoot = repository.getRoot();
        final DirectoryStream<org.uberfire.java.nio.file.Path> nioRepositoryPaths = ioService.newDirectoryStream( Paths.convert( repositoryRoot ) );
        for ( org.uberfire.java.nio.file.Path nioRepositoryPath : nioRepositoryPaths ) {
            if ( Files.isDirectory( nioRepositoryPath ) ) {
                final org.uberfire.backend.vfs.Path projectPath = Paths.convert( nioRepositoryPath );
                final Project project = projectService.resolveProject( projectPath );
                if ( project != null ) {
                    repositoryProjects.add( project );
                }
            }
        }
        return repositoryProjects;
    }

}
