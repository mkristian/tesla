/**********************************************************************************************************************
 * Copyright (c) 2011 to original author or authors                                                                   *
 * All rights reserved. This program and the accompanying materials                                                   *
 * are made available under the terms of the Eclipse Public License v1.0                                              *
 * which accompanies this distribution, and is available at                                                           *
 *   http://www.eclipse.org/legal/epl-v10.html                                                                        *
 **********************************************************************************************************************/
package org.eclipse.tesla.shell.preparator;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Collection;

/**
 * A {@link Method} {@link ActionInjector}.
 *
 * @author <a href="mailto:adreghiciu@gmail.com">Alin Dreghiciu</a>
 * @since 3.0.4
 */
public class ActionMethodInjector
    implements ActionInjector
{

    // ----------------------------------------------------------------------
    // Implementation fields
    // ----------------------------------------------------------------------

    private Object instance;

    private final Method method;

    // ----------------------------------------------------------------------
    // Constructors
    // ----------------------------------------------------------------------

    public ActionMethodInjector( final Object instance, final Method method )
    {
        this.instance = instance;
        this.method = method;
    }

    // ----------------------------------------------------------------------
    // Public methods
    // ----------------------------------------------------------------------

    @Override
    public Type getGenericType()
    {
        return method.getGenericParameterTypes()[0];
    }

    @Override
    public Class<?> getType()
    {
        return method.getParameterTypes()[0];
    }

    @Override
    public void set( final Object value )
        throws Exception
    {
        method.setAccessible( true );
        try
        {
            if ( value != null
                && value instanceof Collection
                && !( method.getParameterTypes()[0].isArray()
                || Collection.class.isAssignableFrom( method.getParameterTypes()[0] ) ) )
            {
                for ( final Object item : (Collection) value )
                {
                    method.invoke( instance, item );
                }
            }
            else if ( value != null
                && value.getClass().isArray()
                && !( method.getParameterTypes()[0].isArray()
                || Collection.class.isAssignableFrom( method.getParameterTypes()[0] ) ) )
            {
                for ( final Object item : (Object[]) value )
                {
                    method.invoke( instance, item );
                }
            }
            else
            {
                method.invoke( instance, value );
            }
        }
        catch ( InvocationTargetException e )
        {
            throw new Exception( e.getMessage(), e.getTargetException() );
        }
    }

    @Override
    public Object get()
        throws IllegalAccessException
    {
        return null;
    }

}
