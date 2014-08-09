package org.ifcx.gondor

import org.ggf.drmaa.JobTemplate
import org.ifcx.drmaa.GondorJobTemplate

/**
 * Created with IntelliJ IDEA.
 * User: jim
 * Date: 2/22/14
 * Time: 9:48 PM
 * To change this template use File | Settings | File Templates.
 */

class Job {
    WorkflowImpl workflow
    String id
    String comment
    File templateFile
    File workingDir
    Integer procId
    Set<String> parentIds = []
    String preScript = "scripts/job_wrapper.sh"
    Set<Integer> preSkipCodes = [142]
    String postScript = preScript

    Job init(GondorJobTemplate jt) {
        def (jobTemplateName, jobComment, jobTemplateFile) = getJobTemplateFile(jt)
        id = nextJobId(jobTemplateName)
        comment = jobComment
        /*procId: -1,*/
        templateFile = jobTemplateFile
        parentIds.addAll(workflow.parentJobIds)
        this
    }

    void printToDAG(PrintWriter printer) {
        printer.println '# ' + comment
        printer.println "JOB ${id} ${templateFile.path}" + (workingDir ? ' DIR ' + workingDir.path : '')

        def vars = [:]

        if (procId != null) {
            vars._GONDOR_PROCID = procId
        }

        if (vars) {
            printer.println "VARS ${id} ${vars.collect { k, v -> "$k=\"$v\"" }.join(' ')}"
        }

        if (preScript) printer.println "SCRIPT PRE $id $preScript pre " +
                '$JOB $RETRY $MAX_RETRIES $DAG_STATUS $FAILED_COUNT '

        preSkipCodes.each { printer.println "PRE_SKIP $id $it" }

        if (postScript) printer.println "SCRIPT POST $id $postScript post " +
                '$JOB $RETRY $MAX_RETRIES $DAG_STATUS $FAILED_COUNT ' +
                '$JOBID $RETURN $PRE_SCRIPT_RETURN '
    }

    String nextJobId(String jobName) {
        jobName + String.format("_%04d", workflow.nextJobNumber())
    }

    String nextJobTemplateName(String jobTemplateName) {
        String.format("${jobTemplateName}_%03d", workflow.nextJobTemplateNumber())
    }

    /**
     * Create a Condor submit file for the job template.
     *
     * @param jt a {@link JobTemplate}
     * @param number the number of times to execute the job
     * @see "condor_submit man page"
     * @return a {@link File} for the submit file created
     * @throws Exception
     */
    void writeJobTemplateFile(JobTemplate jt, File jobTemplateFile) throws Exception {
        jobTemplateFile.withPrintWriter { printer ->
            printer.println """### BEGIN Condor Job Template File ###
# Generated by ${workflow.drmSystem} version ${workflow.version} using ${workflow.drmaaImplementation} on ${new Date()}
#
Universe=vanilla
Executable=${jt.remoteCommand}
Log=${workflow.logFile}
"""
            // Handle the case of the user/caller setting the environment for the job.
            if (jt.jobEnvironment) {
                // This is the Condor directive for setting the job environment.
                // See <file://localhost/Users/jim/Downloads/condor-V8_0_5-Manual/condor_submit.html#man-condor-submit-environment>.
                // We use the "new" format of course which involves escaping ' and " by repeating them and
                // surrounding spaces with a pair of single quotes.
                def envArgsValue = jt.jobEnvironment.collect { String k, String v ->
                    if (v.contains("'") || v.contains(" ")) {
                        k + "='" + v.replace('"', '""').replace("'", "''") + "'"
                    } else {
                        k + '=' + v.replace('"', '""')
                    }
                }
                printer.println "Environment = \"${envArgsValue.join(' ')}\""
            }

            // Here we handle the job arguments, if any have been supplied.
            // We try to adhere to the "new" way of specifying the arguments
            // as explained in the 'condor_submit' man page.
            if (jt.args) {
                def args = jt.args.collect { String arg ->
                    if (arg.contains("\"")) {
                        arg = arg.replace("\"", "\"\"");
                    }
                    // Replace ticks with double ticks
                    if (arg.contains("\'")) {
                        arg = arg.replace("\'", "\'\'");
                    }
                    if (arg.contains(" ")) {
                        arg = "'" + arg + "'"
                    }
                    arg
                }
                printer.println "Arguments=\"${args.join(' ')}\""
            }

            // If the working directory has been set, configure it.
            if (jt.workingDirectory != null) {
                printer.println "InitialDir=" + replacePathPlaceholders(jt.workingDirectory)
            }

            // Handle any native specifications that have been set
            if (jt.getNativeSpecification() != null) {
                printer.println(jt.getNativeSpecification())
            }

            // Handle the job category.
            //TODO: Could use priority or rank for this.
            if (jt.getJobCategory() != null) {
                printer.println("# Category=" + jt.getJobCategory())
            }

            // If the caller has specified a start time, then we add special
            // Condor settings into the submit file. Otherwise, don't do anything
            // special...
            if (jt.getStartTime() != null) {
                long time = (jt.getStartTime().getTimeInMillis() + 500) / 1000;
                printer.println("PeriodicRelease=(CurrentTime > " + time + ")");

//                // TODO: Is this correct?  If we submit with a hold will release happen?
//                if (jt.getJobSubmissionState() != JobTemplate.HOLD_STATE) {
//                    writer.println "Hold=true"
//                }
            }

            // Handle the naming of the job.
            if (jt.jobName) {
                // TODO: The C implementation has a "+" character in front of the
                // directive. We add it here as well. Find out why (or if) this is necessary.
                printer.println("+JobName=" + jt.jobName);
            }

            // Handle the job input path. Care must be taken to replace DRMAA tokens
            // with tokens that Condor understands.
            if (jt.getInputPath() != null) {
                String input = replacePathPlaceholders(jt.inputPath)
                printer.println("Input=" + input)

                // Check whether to transfer the input files
                if (jt.transferFiles?.inputStream) {
                    printer.println("transfer_input_files=" + input);
                }
            }

            // Handle the job output path. Care must be taken to replace DRMAA tokens
            // with tokens that Condor understands.
            if (jt.outputPath) {
                String output = replacePathPlaceholders(jt.outputPath)
                printer.println("Output=" + output);

                // Check if we need to join input and output files
                if (jt.joinFiles) {
                    printer.println("# Joining Input and Output");
                    printer.println("Error=" + output);
                }
            }

            // Handle the error path if specified. Do token replacement if necessary.
            if (! jt.joinFiles && jt.errorPath) {
                String errorPath = replacePathPlaceholders(jt.errorPath)
                printer.println("Error=" + errorPath)
            }

            if (jt.transferFiles?.outputStream) {
                printer.println("should_transfer_files=IF_NEEDED");
                printer.println("when_to_transfer_output=ON_EXIT");
            }

            // Send email notifications?
            if (jt.getBlockEmail()) {
                printer.println("Notification=Never");
            }

            // Documentation is a bit thin, but it seems Condor will accept multiple
            // email addresses separated by a comma.
            Set<String> emails = []
            if (workflow.contact) emails.add(workflow.contact)
            if (jt.email) emails.addAll(jt.email)
            if (emails) {
                printer.println("Notify_user=" + emails.join(","))
            }

            // Should jobs be submitted into a holding pattern
            // (don't immediately start running them)?
            if (jt.getJobSubmissionState() == JobTemplate.HOLD_STATE) {
                printer.println "Hold=true"
            }

            // Every Condor submit file needs a Queue directive to make the job go.
            // Array jobs will have a Queue count greater than 1.
            printer.println "Queue"
            printer.println "#"
            printer.println "### END Condor Job Template File ###"
        }
    }

    private String replacePathPlaceholders(String path) {
        path = path.replace(JobTemplate.PARAMETRIC_INDEX, '$(_GONDOR_PROCID)')
        path = path.replace(JobTemplate.HOME_DIRECTORY, '$ENV(HOME)')
        path = path.replace(JobTemplate.WORKING_DIRECTORY, (workingDir ?: workflow.workingDir).path)
        if (path.startsWith(":")) {
            path = path.substring(1);
        }
        path
    }


    def getJobTemplateFile(JobTemplate jt0) {
//        if (jobTemplateMap.containsKey(jt0)) {
//            return jobTemplateFiles[jobTemplateMap[jt0]]
//        }
//
//        if (jobTemplateMap.values().find { it.jobName.equalsIgnoreCase(jt0.jobName) }) {
//            throw new InvalidJobTemplateException("Job name ${jt0.jobName} used in more than one job template but they are not equivalent.")
//        }
//
//        jt0 = (JobTemplateImpl) jt0.clone()
//        JobTemplate jt1 = (JobTemplateImpl) jt0.clone()

        def jt1 = jt0

        def jobTemplateName = nextJobTemplateName(jt1.jobName ?: defaultJobTemplateName(jt1.remoteCommand))

        File jobTemplateFile = new File(workflow.temporaryFilesDir, jobTemplateName + ".job")

        writeJobTemplateFile(jt1, jobTemplateFile)

//        jobTemplateMap[jt0] = jt1
//        jobTemplateFiles[jt1] = jobTemplateFile

        def jobComment = "${jt1.workingDirectory ? 'cd ' + replacePathPlaceholders(jt1.workingDirectory) + ' ' : ''}" +
                "${jt1.remoteCommand} ${jt1.args.join(' ')}" +
                "${jt1.inputPath ? ' <' + replacePathPlaceholders(jt1.inputPath) : ''}" +
                "${jt1.outputPath ? ' >' + replacePathPlaceholders(jt1.outputPath) : ''}" +
                "${jt1.errorPath ? ' 2>' + replacePathPlaceholders(jt1.errorPath) : ''}"

        [jobTemplateName, jobComment, jobTemplateFile]
    }

    static String defaultJobTemplateName(String path) {
        def name = path.replaceAll(/[^A-Za-z_]/, '_')
        if (!name.startsWith('_')) name = '_' + name
        name
    }
}
