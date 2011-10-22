package org.apache.maven.incremental.internal;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.codehaus.plexus.logging.Logger;
import org.eclipse.tesla.incremental.BuildContext;
import org.eclipse.tesla.incremental.BuildContextManager;
import org.eclipse.tesla.incremental.Digester;

/**
 * Maven specific BuildContext factory interface.
 * <p>
 * Differences compared to BuildContextManager
 * <ul>
 * <li>Conventional location of incremental build state under ${build.build.directory}/incremental. In the future, this
 * may become configurable via well-known project property.</li>
 * <li>Automatically detect configuration changes based on
 * <ul>
 * <li>Maven plugin artifacts GAVs, file sizes and timestamps</li>
 * <li>Project effective pom.xml. In the future, this may be narrowed down.</li>
 * <li>Maven session execution, i.e. user and system, properties.</li>
 * </ul>
 * </li>
 * </ul>
 * 
 * @TODO decide how to handle volatile properties like ${maven.build.timestamp}. Should we always ignore them? Are there
 *       cases where output has to be always regenerated just to include new build timestamp?
 */
@Component( role = MavenBuildContextManager.class )
public class MavenBuildContextManager
{
    @Requirement
    private BuildContextManager manager;

    @Requirement
    private Logger logger;

    public BuildContext newContext( MavenSession session, MojoExecution execution )
    {
        MavenProject project = session.getCurrentProject();

        File outputDirectory = project.getBasedir(); // @TODO really need to get rid of this!

        File stateDirectory = new File( project.getBuild().getDirectory(), "incremental" );

        String builderId = execution.getMojoDescriptor().getId() + ":" + execution.getExecutionId();

        BuildContext context = manager.newContext( outputDirectory, stateDirectory, builderId );

        Digester digester = context.newDigester();

        // plugin artifacts define behaviour, rebuild whenever behaviour changes
        for ( Artifact artifact : execution.getMojoDescriptor().getPluginDescriptor().getArtifacts() )
        {
            digester.strings( artifact.getGroupId(), artifact.getArtifactId(), artifact.getVersion() );
            digester.file( artifact.getFile() );
        }

        // effective pom.xml defines project configuration, rebuild whenever project configuration changes
        // we can't be more specific here because mojo can access entire project model, not just its own configuration
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        try
        {
            new MavenXpp3Writer().write( buf, project.getModel() );
        }
        catch ( IOException e )
        {
            // can't happen
        }
        digester.bytes( buf.toByteArray() );

        // execution properties define build parameters passed in from command line and jvm used
        SortedMap<Object, Object> executionProperties = new TreeMap<Object, Object>( session.getExecutionProperties() );
        for ( Map.Entry<Object, Object> property : executionProperties.entrySet() )
        {
            String key = property.getKey().toString();

            // Environment has PID of java process (env.JAVA_MAIN_CLASS_<PID>), SSH_AGENT_PID, unique TMPDIR (on OSX)
            // and other volatile variables.
            if ( !key.startsWith( "env." ) )
            {
                digester.strings( key, property.getValue().toString() );
            }
        }

        boolean full = context.setConfiguration( digester.finish() );

        if ( logger.isDebugEnabled() )
        {
            logger.debug( "New " + ( full ? "full" : "incremental" ) + " BuildContext for " + execution.toString() );
        }

        return context;
    }

}
