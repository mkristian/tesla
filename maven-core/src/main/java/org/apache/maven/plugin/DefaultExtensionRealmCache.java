package org.apache.maven.plugin;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.maven.project.ExtensionDescriptor;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.classworlds.realm.ClassRealm;
import org.codehaus.plexus.component.annotations.Component;
import org.sonatype.aether.artifact.Artifact;

/**
 * Default extension realm cache implementation. Assumes cached data does not change.
 */
@Component( role = ExtensionRealmCache.class )
public class DefaultExtensionRealmCache
    implements ExtensionRealmCache
{

    private static class CacheKey
        implements Key
    {

        private final List<File> files;

        private final List<Long> timestamps;

        private final List<Long> sizes;

        private final List<String> ids;

        private final ClassLoader sessionRealm;

        private final int hashCode;

        public CacheKey( List<? extends Artifact> extensionArtifacts, ClassLoader sessionRealm )
        {
            this.files = new ArrayList<File>( extensionArtifacts.size() );
            this.timestamps = new ArrayList<Long>( extensionArtifacts.size() );
            this.sizes = new ArrayList<Long>( extensionArtifacts.size() );
            this.ids = new ArrayList<String>( extensionArtifacts.size() );
            this.sessionRealm = sessionRealm;

            for ( Artifact artifact : extensionArtifacts )
            {
                File file = artifact.getFile();
                files.add( file );
                timestamps.add( ( file != null ) ? Long.valueOf( file.lastModified() ) : Long.valueOf( 0 ) );
                sizes.add( ( file != null ) ? Long.valueOf( file.length() ) : Long.valueOf( 0 ) );
                ids.add( artifact.getVersion() );
            }

            this.hashCode =
                31 * files.hashCode() + 31 * ids.hashCode() + 31 * timestamps.hashCode() + 31 * sizes.hashCode()
                    + ( sessionRealm != null ? sessionRealm.hashCode() : 0 );
        }

        @Override
        public int hashCode()
        {
            return hashCode;
        }

        @Override
        public boolean equals( Object o )
        {
            if ( o == this )
            {
                return true;
            }

            if ( !( o instanceof CacheKey ) )
            {
                return false;
            }

            CacheKey other = (CacheKey) o;

            return ids.equals( other.ids ) && files.equals( other.files ) && timestamps.equals( other.timestamps )
                && sizes.equals( other.sizes )
                && ( sessionRealm == null ? other.sessionRealm == null : sessionRealm.equals( other.sessionRealm ) );
        }

        @Override
        public String toString()
        {
            return files.toString();
        }

    }

    private final Map<Key, CacheRecord> cache = new HashMap<Key, CacheRecord>();

    public Key createKey( List<? extends Artifact> extensionArtifacts, ClassLoader sessionRealm )
    {
        return new CacheKey( extensionArtifacts, sessionRealm );
    }

    public CacheRecord get( Key key )
    {
        return cache.get( key );
    }

    public CacheRecord put( Key key, ClassRealm extensionRealm, ExtensionDescriptor extensionDescriptor )
    {
        if ( extensionRealm == null )
        {
            throw new NullPointerException();
        }

        if ( cache.containsKey( key ) )
        {
            throw new IllegalStateException( "Duplicate extension realm for extension " + key );
        }

        CacheRecord record = new CacheRecord( extensionRealm, extensionDescriptor );

        cache.put( key, record );

        return record;
    }

    public void flush()
    {
        cache.clear();
    }

    public void register( MavenProject project, CacheRecord record )
    {
        // default cache does not track extension usage
    }

}
