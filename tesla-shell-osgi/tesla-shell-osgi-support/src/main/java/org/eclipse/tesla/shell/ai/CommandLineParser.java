/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.eclipse.tesla.shell.ai;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.Reader;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.felix.gogo.commands.Action;
import org.apache.felix.gogo.commands.Argument;
import org.apache.felix.gogo.commands.Command;
import org.apache.felix.gogo.commands.Option;
import org.apache.felix.gogo.commands.basic.ActionPreparator;
import org.apache.felix.gogo.commands.converter.DefaultConverter;
import org.apache.felix.service.command.CommandSession;
import org.apache.karaf.shell.console.NameScoping;
import org.eclipse.tesla.shell.ai.validation.ArgumentConversionException;
import org.eclipse.tesla.shell.ai.validation.MultipleArgumentsWithSameIndexException;
import org.eclipse.tesla.shell.ai.validation.OptionConversionException;
import org.eclipse.tesla.shell.ai.validation.UndefinedOptionException;
import org.fusesource.jansi.Ansi;
import jline.Terminal;
import sun.awt.util.IdentityArrayList;

public class CommandLineParser
    implements ActionPreparator
{

    public boolean prepare( final Action action, final CommandSession session, final List<Object> params )
        throws Exception
    {
        // Introspect action for extraction of command
        final Command command = getCommand( action );

        // Introspect action for extraction of arguments
        final List<ArgumentBinding> arguments = new ArrayList<ArgumentBinding>( getArguments( action ) );
        // sort arguments by index and ensure that index is unique
        sortArguments( command, arguments );

        // Introspect action for extraction of options
        final List<OptionBinding> options = getOptions( action );

        if ( isHelp( params ) )
        {
            printUsage( session, action, options, arguments, System.out );
            return false;
        }

        // start considering all params as arguments
        final List<Object> argumentValues = new IdentityArrayList<Object>(
            params == null ? Collections.emptyList() : params
        );
        // then extract options out
        final Map<String, Object> optionValues = extractOptions( findSwitches( options ), argumentValues );

        injectOptions( action, session, command, options, optionValues );

        injectArguments( action, session, command, arguments, argumentValues );

//        // Populate
//        Map<Option, Object> optionValues = new HashMap<Option, Object>();
//        Map<Argument, Object> argumentValues = new HashMap<Argument, Object>();
//        boolean processOptions = true;
//        int argIndex = 0;
//        for ( Iterator<Object> it = params.iterator(); it.hasNext(); )
//        {
//            Object param = it.next();
//            // Check for help
//            if ( HelpOption.INSTANCE.name().equals( param )
//                || Arrays.asList( HelpOption.INSTANCE.aliases() ).contains( param ) )
//            {
//                printUsage( session, action, options, arguments, System.out );
//                return false;
//            }
//            if ( processOptions && param instanceof String && ( (String) param ).startsWith( "-" ) )
//            {
//                boolean isKeyValuePair = ( (String) param ).indexOf( '=' ) != -1;
//                String name;
//                Object value = null;
//                if ( isKeyValuePair )
//                {
//                    name = ( (String) param ).substring( 0, ( (String) param ).indexOf( '=' ) );
//                    value = ( (String) param ).substring( ( (String) param ).indexOf( '=' ) + 1 );
//                }
//                else
//                {
//                    name = (String) param;
//                }
//                OptionBinding optionBinding = null;
//                Option option = null;
//                for ( OptionBinding binding : options )
//                {
//                    final Option opt = binding.getOption();
//                    if ( name.equals( opt.name() ) || Arrays.asList( opt.aliases() ).contains( name ) )
//                    {
//                        option = opt;
//                        optionBinding = binding;
//                        break;
//                    }
//                }
//                if ( option == null )
//                {
//                    throw new UndefinedOptionException( command, param );
//                }
//                ActionInjector injector = optionBinding.getInjector();
//                if ( value == null && ( injector.getType() == boolean.class || injector.getType() == Boolean.class ) )
//                {
//                    value = Boolean.TRUE;
//                }
//                if ( value == null && it.hasNext() )
//                {
//                    value = it.next();
//                }
//                if ( value == null )
//                {
//                    throw new MissingValueException( command, param );
//                }
//                if ( option.multiValued() )
//                {
//                    List<Object> l = (List<Object>) optionValues.get( option );
//                    if ( l == null )
//                    {
//                        l = new ArrayList<Object>();
//                        optionValues.put( option, l );
//                    }
//                    l.add( value );
//                }
//                else
//                {
//                    optionValues.put( option, value );
//                }
//            }
//            else
//            {
//                processOptions = false;
//                if ( argIndex >= arguments.size() )
//                {
//                    throw new TooManyArgumentsException( command );
//                }
//                ArgumentBinding argumentBinding = arguments.get( argIndex );
//                if ( argumentBinding.getArgument().multiValued() )
//                {
//                    List<Object> l = (List<Object>) argumentValues.get( argumentBinding.getArgument() );
//                    if ( l == null )
//                    {
//                        l = new ArrayList<Object>();
//                        argumentValues.put( argumentBinding.getArgument(), l );
//                    }
//                    l.add( param );
//                }
//                else
//                {
//                    argIndex++;
//                    argumentValues.put( argumentBinding.getArgument(), param );
//                }
//            }
//        }
//        // Check required arguments / options
//        for ( OptionBinding binding : options )
//        {
//            final Option option = binding.getOption();
//            if ( option.required() && optionValues.get( option ) == null )
//            {
//                throw new MissingOptionException( command, option );
//            }
//        }
//        for ( ArgumentBinding binding : arguments )
//        {
//            final Argument argument = binding.getArgument();
//            if ( argument.required() && argumentValues.get( argument ) == null )
//            {
//                throw new MissingArgumentException( command, argument );
//            }
//        }
        return true;
    }

    private boolean isHelp( final List<Object> params )
    {
        if ( params != null )
        {
            for ( final Object param : params )
            {
                if ( "--help".equals( param ) )
                {
                    return true;
                }
            }
        }
        return false;
    }

    private List<String> findSwitches( final List<OptionBinding> bindings )
    {
        final ArrayList<String> switches = new ArrayList<String>();
        for ( final OptionBinding binding : bindings )
        {
            final Class<?> optionType = binding.getInjector().getType();
            if ( optionType == boolean.class || optionType == Boolean.class )
            {
                final Option option = binding.getOption();
                switches.add( option.name() );
                if ( option.aliases() != null )
                {
                    for ( final String name : option.aliases() )
                    {
                        if ( name != null && name.trim().length() > 0 )
                        {
                            switches.add( name );
                        }
                    }
                }
            }
        }
        return switches;
    }

    private void injectArguments( final Action action,
                                  final CommandSession session,
                                  final Command command,
                                  final List<ArgumentBinding> arguments,
                                  final List<Object> argumentValues )
        throws Exception
    {
//        for ( Map.Entry<Argument, Object> entry : argumentValues.entrySet() )
//        {
//            ActionInjector injector = injectorOf( entry.getKey(), arguments );
//
//            try
//            {
//                final Object value = convert( action, session, entry.getValue(), injector.getGenericType() );
//                injector.set( value );
//            }
//            catch ( Exception e )
//            {
//                throw new ArgumentConversionException(
//                    command, entry.getKey(), entry.getValue(), injector.getGenericType(), e
//                );
//            }
//        }
    }

    private void injectOptions( final Action action,
                                final CommandSession session,
                                final Command command,
                                final List<OptionBinding> options,
                                final Map<String, Object> optionValues )
        throws Exception
    {
        final Map<String, OptionBinding> nameToBinding = mapOptionsByName( options );
        // Convert and inject values
        for ( Map.Entry<String, Object> entry : optionValues.entrySet() )
        {
            final OptionBinding binding = nameToBinding.get( entry.getKey() );
            if ( binding == null )
            {
                throw new UndefinedOptionException( command, entry.getKey() );
            }
            final ActionInjector injector = binding.getInjector();
            try
            {
                final Object value = convert( action, session, entry.getValue(), injector.getGenericType() );
                injector.set( value );
            }
            catch ( Exception e )
            {
                throw new OptionConversionException(
                    command, binding.getOption(), entry.getValue(), injector.getGenericType(), e
                );
            }
        }
    }

    private Map<String, OptionBinding> mapOptionsByName( final List<OptionBinding> bindings )
    {
        final Map<String, OptionBinding> nameToBinding = new HashMap<String, OptionBinding>();
        for ( final OptionBinding binding : bindings )
        {
            final Option option = binding.getOption();
            if ( nameToBinding.put( option.name(), binding ) != null )
            {
                // TODO throw a duplicate option name exception
            }
            if ( option.aliases() != null )
            {
                for ( final String name : option.aliases() )
                {
                    if ( name != null && name.trim().length() > 0 )
                    {
                        if ( nameToBinding.put( name, binding ) != null )
                        {
                            // TODO throw a duplicate option name exception
                        }
                    }
                }
            }
        }
        return nameToBinding;
    }

    private Map<String, Object> extractOptions( final List<String> switchesNames, final List<Object> argumentValues )
    {
        final Map<String, Object> optionValues = new HashMap<String, Object>();
        final List<Object> copyOfArgumentValues = new ArrayList<Object>( argumentValues );
        int i = -1;
        while ( i < copyOfArgumentValues.size() - 1 )
        {
            i++;

            final Object value = copyOfArgumentValues.get( i );
            if ( value instanceof String )
            {
                final String valueAsString = (String) value;

                // do we have an option?
                if ( valueAsString.startsWith( "-" ) )
                {
                    // we have an option name, remove it from arguments list
                    argumentValues.remove( value );

                    // everything after "--" is an argument
                    if ( valueAsString.equals( "--" ) )
                    {
                        break;
                    }

                    // look ahead
                    final Object lookAheadValue = copyOfArgumentValues.get( i + 1 );
                    if ( lookAheadValue instanceof String )
                    {
                        final String lookAheadAsString = (String) lookAheadValue;

                        if ( switchesNames.contains( valueAsString ) )
                        {
                            if ( !"true".equalsIgnoreCase( lookAheadAsString )
                                && !"false".equalsIgnoreCase( lookAheadAsString )
                                && ( lookAheadAsString.startsWith( "-" )
                                || lookAheadAsString.equals( "--" )
                                || !Boolean.valueOf( lookAheadAsString ) ) )
                            {
                                optionValues.put( valueAsString, true );
                                continue;
                            }
                        }
                    }

                    // we have a value for the option, remove it from arguments list
                    argumentValues.remove( lookAheadValue );
                    // and add it as an option
                    optionValues.put( valueAsString, lookAheadValue );
                    // skip the value that follows as we already know is an option value
                }
            }
        }

        return optionValues;
    }

    private void sortArguments( final Command command, final List<ArgumentBinding> arguments )
        throws Exception
    {
        try
        {
            Collections.sort( arguments, new Comparator<ArgumentBinding>()
            {
                @Override
                public int compare( final ArgumentBinding ab1, final ArgumentBinding ab2 )
                {
                    final int result =
                        Integer.valueOf( ab1.getArgument().index() ).compareTo( ab2.getArgument().index() );
                    if ( result == 0 )
                    {
                        throw new IllegalArgumentException(
                            new MultipleArgumentsWithSameIndexException(
                                command, ab1.getArgument(), ab2.getArgument()
                            )
                        );
                    }
                    return result;
                }
            } );
        }
        catch ( IllegalArgumentException e )
        {
            throw (MultipleArgumentsWithSameIndexException) e.getCause();
        }
    }

    private ActionInjector injectorOf( final Option option, final List<OptionBinding> options )
    {
        for ( final OptionBinding binding : options )
        {
            if ( binding.getOption().equals( option ) )
            {
                return binding.getInjector();
            }
        }
        throw new IllegalStateException( "Could not find injector of option " + option );
    }

    private ActionInjector injectorOf( final Argument argument, final List<ArgumentBinding> arguments )
    {
        for ( final ArgumentBinding binding : arguments )
        {
            if ( binding.getArgument().equals( argument ) )
            {
                return binding.getInjector();
            }
        }
        throw new IllegalStateException( "Could not find injector of argument " + argument );
    }

    protected Command getCommand( final Action action )
    {
        return action.getClass().getAnnotation( Command.class );
    }

    protected List<OptionBinding> getOptions( final Action action )
    {
        final List<OptionBinding> options = new ArrayList<OptionBinding>();
        for ( Class type = action.getClass(); type != null; type = type.getSuperclass() )
        {
            for ( Field field : type.getDeclaredFields() )
            {
                final Option option = field.getAnnotation( Option.class );
                if ( option != null )
                {
                    options.add( new OptionBinding( option, new ActionFieldInjector( action, field ) ) );
                }
            }
        }
        return options;
    }

    protected List<ArgumentBinding> getArguments( final Action action )
    {
        final List<ArgumentBinding> arguments = new ArrayList<ArgumentBinding>();
        for ( Class type = action.getClass(); type != null; type = type.getSuperclass() )
        {
            for ( Field field : type.getDeclaredFields() )
            {
                Argument argument = field.getAnnotation( Argument.class );
                if ( argument != null )
                {
                    if ( Argument.DEFAULT.equals( argument.name() ) )
                    {
                        argument = new UnnamedArgument( field.getName(), argument );
                    }
                    arguments.add( new ArgumentBinding( argument, new ActionFieldInjector( action, field ) ) );
                }
            }
        }
        return arguments;
    }

    protected void printUsage( final CommandSession session,
                               final Action action,
                               final List<OptionBinding> options,
                               final List<ArgumentBinding> arguments,
                               final PrintStream out )
    {
        Command command = getCommand( action );
        Terminal term = session != null ? (Terminal) session.get( ".jline.terminal" ) : null;

        boolean globalScope = NameScoping.isGlobalScope( session, command.scope() );
        if ( command != null && ( command.description() != null || command.name() != null ) )
        {
            out.println( Ansi.ansi().a( Ansi.Attribute.INTENSITY_BOLD ).a( "DESCRIPTION" ).a( Ansi.Attribute.RESET ) );
            out.print( "        " );
            if ( command.name() != null )
            {
                if ( globalScope )
                {
                    out.println(
                        Ansi.ansi().a( Ansi.Attribute.INTENSITY_BOLD ).a( command.name() ).a( Ansi.Attribute.RESET ) );
                }
                else
                {
                    out.println( Ansi.ansi().a( command.scope() ).a( ":" ).a( Ansi.Attribute.INTENSITY_BOLD ).a(
                        command.name() ).a(
                        Ansi.Attribute.RESET ) );
                }
                out.println();
            }
            out.print( "\t" );
            out.println( command.description() );
            out.println();
        }
        StringBuffer syntax = new StringBuffer();
        if ( command != null )
        {
            if ( globalScope )
            {
                syntax.append( command.name() );
            }
            else
            {
                syntax.append( String.format( "%s:%s", command.scope(), command.name() ) );
            }
        }
        if ( options.size() > 0 )
        {
            syntax.append( " [options]" );
        }
        if ( arguments.size() > 0 )
        {
            syntax.append( ' ' );
            for ( ArgumentBinding binding : arguments )
            {
                final Argument argument = binding.getArgument();
                if ( !argument.required() )
                {
                    syntax.append( String.format( "[%s] ", argument.name() ) );
                }
                else
                {
                    syntax.append( String.format( "%s ", argument.name() ) );
                }
            }
        }

        out.println( Ansi.ansi().a( Ansi.Attribute.INTENSITY_BOLD ).a( "SYNTAX" ).a( Ansi.Attribute.RESET ) );
        out.print( "        " );
        out.println( syntax.toString() );
        out.println();
        if ( arguments.size() > 0 )
        {
            out.println( Ansi.ansi().a( Ansi.Attribute.INTENSITY_BOLD ).a( "ARGUMENTS" ).a( Ansi.Attribute.RESET ) );
            for ( ArgumentBinding binding : arguments )
            {
                final Argument argument = binding.getArgument();
                out.print( "        " );
                out.println(
                    Ansi.ansi().a( Ansi.Attribute.INTENSITY_BOLD ).a( argument.name() ).a( Ansi.Attribute.RESET ) );
                printFormatted( "                ", argument.description(), term != null ? term.getWidth() : 80, out );
                if ( !argument.required() )
                {
                    if ( argument.valueToShowInHelp() != null && argument.valueToShowInHelp().length() != 0 )
                    {
                        try
                        {
                            if ( Argument.DEFAULT_STRING.equals( argument.valueToShowInHelp() ) )
                            {
                                Object o = binding.getInjector().get();
                                printObjectDefaultsTo( out, o );
                            }
                            else
                            {
                                printDefaultsTo( out, argument.valueToShowInHelp() );
                            }
                        }
                        catch ( Throwable t )
                        {
                            // Ignore
                        }
                    }
                }
            }
            out.println();
        }
        if ( options.size() > 0 )
        {
            out.println( Ansi.ansi().a( Ansi.Attribute.INTENSITY_BOLD ).a( "OPTIONS" ).a( Ansi.Attribute.RESET ) );
            for ( OptionBinding binding : options )
            {
                final Option option = binding.getOption();
                String opt = option.name();
                for ( String alias : option.aliases() )
                {
                    opt += ", " + alias;
                }
                out.print( "        " );
                out.println( Ansi.ansi().a( Ansi.Attribute.INTENSITY_BOLD ).a( opt ).a( Ansi.Attribute.RESET ) );
                printFormatted( "                ", option.description(), term != null ? term.getWidth() : 80, out );
                if ( option.valueToShowInHelp() != null && option.valueToShowInHelp().length() != 0 )
                {
                    try
                    {
                        if ( Option.DEFAULT_STRING.equals( option.valueToShowInHelp() ) )
                        {
                            Object o = binding.getInjector().get();
                            printObjectDefaultsTo( out, o );
                        }
                        else
                        {
                            printDefaultsTo( out, option.valueToShowInHelp() );
                        }
                    }
                    catch ( Throwable t )
                    {
                        // Ignore
                    }
                }
            }
            out.println();
        }
        if ( command.detailedDescription().length() > 0 )
        {
            out.println( Ansi.ansi().a( Ansi.Attribute.INTENSITY_BOLD ).a( "DETAILS" ).a( Ansi.Attribute.RESET ) );
            String desc = loadDescription( action.getClass(), command.detailedDescription() );
            printFormatted( "        ", desc, term != null ? term.getWidth() : 80, out );
        }
    }

    private void printObjectDefaultsTo( PrintStream out, Object o )
    {
        if ( o != null
            && ( !( o instanceof Boolean ) || ( (Boolean) o ) )
            && ( !( o instanceof Number ) || ( (Number) o ).doubleValue() != 0.0 ) )
        {
            printDefaultsTo( out, o.toString() );
        }
    }

    private void printDefaultsTo( PrintStream out, String value )
    {
        out.print( "                (defaults to " );
        out.print( value );
        out.println( ")" );
    }

    protected String loadDescription( Class clazz, String desc )
    {
        if ( desc.startsWith( "classpath:" ) )
        {
            InputStream is = clazz.getResourceAsStream( desc.substring( "classpath:".length() ) );
            if ( is == null )
            {
                is = clazz.getClassLoader().getResourceAsStream( desc.substring( "classpath:".length() ) );
            }
            if ( is == null )
            {
                desc = "Unable to load description from " + desc;
            }
            else
            {
                try
                {
                    Reader r = new InputStreamReader( is );
                    StringWriter sw = new StringWriter();
                    int c;
                    while ( ( c = r.read() ) != -1 )
                    {
                        sw.append( (char) c );
                    }
                    desc = sw.toString();
                }
                catch ( IOException e )
                {
                    desc = "Unable to load description from " + desc;
                }
                finally
                {
                    try
                    {
                        is.close();
                    }
                    catch ( IOException e )
                    {
                        // Ignore
                    }
                }
            }
        }
        return desc;
    }

    // TODO move this to a helper class?
    public static void printFormatted( String prefix, String str, int termWidth, PrintStream out )
    {
        int pfxLen = length( prefix );
        int maxwidth = termWidth - pfxLen;
        Pattern wrap = Pattern.compile( "(\\S\\S{" + maxwidth + ",}|.{1," + maxwidth + "})(\\s+|$)" );
        Matcher m = wrap.matcher( str );
        while ( m.find() )
        {
            out.print( prefix );
            out.println( m.group() );
        }
    }

    public static int length( String str )
    {
        return str.length();
    }

    protected Object convert( Action action, CommandSession session, Object value, Type toType )
        throws Exception
    {
        if ( toType == String.class )
        {
            return value != null ? value.toString() : null;
        }
        return new DefaultConverter( action.getClass().getClassLoader() ).convert( value, toType );
    }

}
