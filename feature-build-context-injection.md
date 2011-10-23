Automatic BuildContext injection and lifecycle management

New BuildContext instance is automatically created and associated with each
project mojo execution and can be injected as a plexus or jsr330 component
to the mojo itself or any of components directly or indirected used by the
mojo.

BuildContex is automatically closed after mojo execution and execution failure
exception is raised if there are uncleared error messages reported to the 
build context.

Digested information about maven plugin artifact and all its dependencies is
automatically stored in the build context and full build is triggered if any
of the artifact changes. Likewise, project effective pom and session execution
properties are stored in the build context and changes to either will result
in full build.

Open questions

* either cleanup tesla-build-avoidance API and implementation or define 
  separate tesla-specific and hopefully easier to understand and use 
  BuildContext interface
