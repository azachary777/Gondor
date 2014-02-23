package org.ifcx.gondor

import org.ggf.drmaa.DrmaaException
import org.ggf.drmaa.InternalException
import org.ggf.drmaa.InvalidJobTemplateException
import org.ggf.drmaa.JobInfo
import org.ggf.drmaa.JobTemplate
import org.ggf.drmaa.Version
import org.ifcx.drmaa.Workflow

public class WorkflowImpl implements Workflow {
    private static final Version VERSION = new Version(0, 1)

    String contact = ""
    String workflowName = "gondor_default_workflow"

    File jobTemplatesDir
    Map<JobTemplate, JobTemplate> jobTemplateMap = [:]
    Map<JobTemplate, File> jobTemplateFiles = [:]

    int job_number = 0
    int job_template_number = 0

    @Override
    void init(String contact) throws DrmaaException {
        this.contact = contact

        jobTemplatesDir = new File(workflowName + ".jobs")

        if (!jobTemplatesDir.exists() && !jobTemplatesDir.mkdirs()) {
            throw new InternalException("Can't create directory $jobTemplatesDir for job templates.")
        }

        if (!jobTemplatesDir.isDirectory()) {
            throw new InternalException("The file $jobTemplatesDir exists where we want to put the job templates dir.")
        }
    }


    @Override
    String getWorkflowName() {
        this.workflowName
    }

    @Override
    void init(String contact, String workflowName) {
        this.workflowName = workflowName
        init(contact)
    }


    @Override
    void exit() throws DrmaaException {

    }

    @Override
    JobTemplate createJobTemplate() throws DrmaaException {
        new JobTemplateImpl()
    }

    @Override
    void deleteJobTemplate(JobTemplate jt) throws DrmaaException {
//        jobTemplateMap.remove(jt)
    }

    String nextJobId(String jobName) {
        jobName + String.format("_%04d", ++job_number)
    }

    String nextJobTemplateName(String jobTemplateName) {
        String.format("+${jobTemplateName}_%04d", ++job_template_number)
    }

    File getJobTemplateFile(JobTemplate jt0) {
        if (jobTemplateMap.containsKey(jt0)) {
            return jobTemplateFiles[jobTemplateMap[jt0]]
        }

        if (jobTemplateMap.values().find { it.jobName.equalsIgnoreCase(jt0.jobName) }) {
            throw new InvalidJobTemplateException("Job name ${jt0.jobName} used in more than one job template but they are not equivalent.")
        }

        jt0 = (JobTemplateImpl) jt0.clone()
        JobTemplate jt1 = (JobTemplateImpl) jt0.clone()

        if (!jt1.jobName) {
            jt1.setGeneratedJobName(nextJobTemplateName(jt1.remoteCommand.replaceAll(/[^A-Za-z_]/, '_')))
        }

        File jobTemplateFile = new File(jobTemplatesDir, jt1.jobName + ".job")

        jobTemplateFile.createNewFile()

        jobTemplateMap[jt0] = jt1
        jobTemplateFiles[jt1] = jobTemplateFile
    }

    @Override
    String runJob(JobTemplate jt) throws DrmaaException {
        File jobTemplateFile = getJobTemplateFile(jt)
        jt = jobTemplateMap[jt]
        def jobId = nextJobId(jt.jobName)
        jobId
    }

    @Override
    List runBulkJobs(JobTemplate jt, int start, int end, int incr) throws DrmaaException {
        File jobTemplateFile = getJobTemplateFile(jt)
        jt = jobTemplateMap[jt]
        String jobName = jt.jobName
        if (incr > end - start) incr = Math.max(end - start, 1)
        def jobIds = (start..end).step(incr).collect { nextJobId(jobName) }
        assert jobIds.size() == Math.floor(((end - start) / incr) + 1)
        jobIds
    }

    @Override
    void control(String jobId, int action) throws DrmaaException {
       
    }

    @Override
    void synchronize(List jobIds, long timeout, boolean dispose) throws DrmaaException {
       
    }

    @Override
    JobInfo wait(String jobId, long timeout) throws DrmaaException {
        return null 
    }

    @Override
    int getJobProgramStatus(String jobId) throws DrmaaException {
        throw new IllegalWorkflowOperation()
    }

    @Override
    String getContact() {
       contact
    }

    @Override
    Version getVersion() {
        VERSION
    }

    @Override
    String getDrmSystem() {
        "Gondor"
    }

    @Override
    String getDrmaaImplementation() {
        "Gondor DRMAA 1.0 + Workflow"
    }
}
