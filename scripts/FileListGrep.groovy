import com.beust.jcommander.Parameter
import groovy.transform.Field
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.ifcx.gondor.Command

@groovy.transform.BaseScript org.ifcx.gondor.WorkflowScript thisScript

// Define a command for /bin/ls.  It takes an optional file path argument.
def ls = command(path:'/bin/ls') { infile 'path' }

// Note that the exit status for grep is 1 if there are no matched lines.
// Gondor does not yet have an option for treating non-zero exit status values as an indication of success.
def grep = command(path:'/usr/bin/grep') {
    // An optional argument for whether the output should have numbered lines.
    // The third argument here is a closure that takes the given parameter value
    // and returns the string that will appear as the command line argument.
    arg 'lineNumbers', Command.OPTIONAL, { it ? '--line-number' : [] }

    // Arguments are currently optional by default, but ere that is overridden as required.
    // The formatting closure may return a list of strings rather than just one.
    // That is used here to provide the '-e' flag and it's associated pattern value.
    arg 'pat', Command.REQUIRED, { ['-e', it as String] }
}

def cat = command(path:'/bin/cat') { infile 'paths' }

@Parameter(names = '--path', description = 'Path to directory for the first file list.')
@InputDirectory @Field File path

@Parameter(names = ['--pattern', '--regex'], description = 'Regular expression to filter with.')
@Field String pattern

@Parameter(names = '--result', description = 'Output file.')
@OutputFile @Field File result

@Parameter(description = 'A list of directory paths for additional file lists')
@InputDirectory @Field List<File> paths

// (ls(path:path) | grep(pat:pattern)) >> result

// (ls() | grep(pat:/est/, lineNumbers:true)) >> new File('grep_with_numbers.txt')

cat(paths:[path, *paths].collect { (ls(path:it) | grep(pat:pattern, lineNumbers:true)).output }) >> result