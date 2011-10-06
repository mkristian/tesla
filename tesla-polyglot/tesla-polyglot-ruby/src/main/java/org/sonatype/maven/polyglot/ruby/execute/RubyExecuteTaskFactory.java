/**
 * 
 */
package org.sonatype.maven.polyglot.ruby.execute;

import java.util.LinkedList;
import java.util.List;

import org.jruby.embed.ScriptingContainer;
import org.sonatype.maven.polyglot.execute.ExecuteTask;

public class RubyExecuteTaskFactory {
    private ScriptingContainer jruby;
    private List<ExecuteTask> tasks = new LinkedList<ExecuteTask>();
    
    public RubyExecuteTaskFactory(ScriptingContainer jruby) {
        this.jruby = jruby;
    }

    public void addExecuteTask(String id, String phase, Object script){
        RubyExecuteTask task = new RubyExecuteTask(jruby);
        task.setId(id);
        task.setPhase(phase);
        task.setScript(script);
        
        this.tasks.add(task);
    }
    
    public List<ExecuteTask> getExecuteTasks(){
        return tasks;
    }
}