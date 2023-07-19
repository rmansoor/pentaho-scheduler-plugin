package org.pentaho.platform.web.http.api.resources.services.importer;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.logging.Log;
import org.pentaho.platform.api.repository2.unified.IPlatformImportBundle;
import org.pentaho.platform.api.repository2.unified.RepositoryFile;
import org.pentaho.platform.plugin.services.importer.ISolutionComponentImportHandler;
import org.pentaho.platform.plugin.services.importer.PlatformImportException;
import org.pentaho.platform.plugin.services.importexport.ImportSession;
import org.pentaho.platform.plugin.services.importexport.exportManifest.ExportManifest;
import org.pentaho.platform.plugin.services.messages.Messages;
import org.pentaho.platform.web.http.api.resources.JobRequest;
import org.pentaho.platform.web.http.api.resources.JobScheduleParam;
import org.pentaho.platform.web.http.api.resources.JobScheduleRequest;

import javax.ws.rs.core.Response;
import java.io.File;
import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SchedulerSolutionImportHandler implements ISolutionComponentImportHandler {

    @Override
    public void importComponent(ExportManifest manifest, Log logger, IPlatformImportBundle bundle) {

    }

    @Override
    public String getComponentName() {
        return null;
    }

    protected void importSchedules( List<JobScheduleRequest> scheduleList ) throws PlatformImportException {
        if ( CollectionUtils.isNotEmpty( scheduleList ) ) {
            SchedulerResource schedulerResource = new SchedulerResource();
            schedulerResource.pause();
            for ( JobScheduleRequest jobScheduleRequest : scheduleList ) {

                boolean jobExists = false;

                List<Job> jobs = getAllJobs( schedulerResource );
                if ( jobs != null ) {

                    //paramRequest to map<String, Serializable>
                    Map<String, Serializable> mapParamsRequest = new HashMap<>();
                    for ( JobScheduleParam paramRequest : jobScheduleRequest.getJobParameters() ) {
                        mapParamsRequest.put( paramRequest.getName(), paramRequest.getValue() );
                    }

                    for ( Job job : jobs ) {

                        if ( ( mapParamsRequest.get( RESERVEDMAPKEY_LINEAGE_ID ) != null )
                                && ( mapParamsRequest.get( RESERVEDMAPKEY_LINEAGE_ID )
                                .equals( job.getJobParams().get( RESERVEDMAPKEY_LINEAGE_ID ) ) ) ) {
                            jobExists = true;
                        }

                        if ( overwriteFile && jobExists ) {
                            JobRequest jobRequest = new JobRequest();
                            jobRequest.setJobId( job.getJobId() );
                            schedulerResource.removeJob( jobRequest );
                            jobExists = false;
                            break;
                        }
                    }
                }

                if ( !jobExists ) {
                    try {
                        Response response = createSchedulerJob( schedulerResource, jobScheduleRequest );
                        if ( response.getStatus() == Response.Status.OK.getStatusCode() ) {
                            if ( response.getEntity() != null ) {
                                // get the schedule job id from the response and add it to the import session
                                ImportSession.getSession().addImportedScheduleJobId( response.getEntity().toString() );
                            }
                        }
                    } catch ( Exception e ) {
                        // there is a scenario where if the file scheduled has a space in the file name, that it won't work. the
                        // di server

                        // replaces spaces with underscores and the export mechanism can't determine if it needs this to happen
                        // or not
                        // so, if we failed to import and there is a space in the path, try again but this time with replacing
                        // the space(s)
                        if ( jobScheduleRequest.getInputFile().contains( " " ) || jobScheduleRequest.getOutputFile()
                                .contains( " " ) ) {
                            getLogger().info( Messages.getInstance()
                                    .getString( "SolutionImportHandler.SchedulesWithSpaces", jobScheduleRequest.getInputFile() ) );
                            File inFile = new File( jobScheduleRequest.getInputFile() );
                            File outFile = new File( jobScheduleRequest.getOutputFile() );
                            String inputFileName = inFile.getParent() + RepositoryFile.SEPARATOR
                                    + inFile.getName().replace( " ", "_" );
                            String outputFileName = outFile.getParent() + RepositoryFile.SEPARATOR
                                    + outFile.getName().replace( " ", "_" );
                            jobScheduleRequest.setInputFile( inputFileName );
                            jobScheduleRequest.setOutputFile( outputFileName );
                            try {
                                if ( !File.separator.equals( RepositoryFile.SEPARATOR ) ) {
                                    // on windows systems, the backslashes will result in the file not being found in the repository
                                    jobScheduleRequest.setInputFile( inputFileName.replace( File.separator, RepositoryFile.SEPARATOR ) );
                                    jobScheduleRequest
                                            .setOutputFile( outputFileName.replace( File.separator, RepositoryFile.SEPARATOR ) );
                                }
                                Response response = createSchedulerJob( schedulerResource, jobScheduleRequest );
                                if ( response.getStatus() == Response.Status.OK.getStatusCode() ) {
                                    if ( response.getEntity() != null ) {
                                        // get the schedule job id from the response and add it to the import session
                                        ImportSession.getSession().addImportedScheduleJobId( response.getEntity().toString() );
                                    }
                                }
                            } catch ( Exception ex ) {
                                // log it and keep going. we should stop processing all schedules just because one fails.
                                getLogger().error( Messages.getInstance()
                                        .getString( "SolutionImportHandler.ERROR_0001_ERROR_CREATING_SCHEDULE", e.getMessage() ), ex );
                            }
                        } else {
                            // log it and keep going. we should stop processing all schedules just because one fails.
                            getLogger().error( Messages.getInstance()
                                    .getString( "SolutionImportHandler.ERROR_0001_ERROR_CREATING_SCHEDULE", e.getMessage() ) );
                        }
                    }
                } else {
                    getLogger().info( Messages.getInstance()
                            .getString( "DefaultImportHandler.ERROR_0009_OVERWRITE_CONTENT", jobScheduleRequest.toString() ) );
                }
            }
            schedulerResource.start();
        }
    }

}
