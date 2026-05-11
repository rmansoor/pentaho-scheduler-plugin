package org.pentaho.platform.plugin.services.importer;

import org.apache.commons.collections.CollectionUtils;
import org.pentaho.platform.api.importexport.IImportHelper;
import org.pentaho.platform.api.importexport.ImportException;
import org.pentaho.platform.api.repository2.unified.RepositoryFile;
import org.pentaho.platform.api.scheduler2.IJob;
import org.pentaho.platform.api.scheduler2.IJobRequest;
import org.pentaho.platform.api.scheduler2.IJobScheduleParam;
import org.pentaho.platform.api.scheduler2.IJobScheduleRequest;
import org.pentaho.platform.api.scheduler2.IScheduler;
import org.pentaho.platform.api.scheduler2.ISchedulerResource;
import org.pentaho.platform.api.scheduler2.JobState;
import org.pentaho.platform.engine.core.system.PentahoSystem;
import org.pentaho.platform.plugin.services.importexport.ImportSession;
import org.pentaho.platform.plugin.services.importexport.ImportExportMetrics;
import org.pentaho.platform.plugin.services.importexport.ImportExportMetrics.Category;
import org.pentaho.platform.plugin.services.messages.Messages;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.core.Response;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Import helper for schedule imports
 * Moved from pentaho-platform's SolutionImportHandler to pentaho-scheduler-plugin
 * 
 * Enhanced with:
 * - ensureScheduleInputFileExists() method to import schedule dependencies from backup
 * - Metrics integration to track schedule imports
 * - Path normalization for consistent file path handling
 */
public class ScheduleImportUtil implements IImportHelper {
  private static final Logger logger = LoggerFactory.getLogger( ScheduleImportUtil.class );
  
  private static final String SCHEDULE_IMPORT_UTIL_NAME = "schedule-import-util";
  private static final String RESERVEDMAPKEY_LINEAGE_ID = "lineage-id";

  private SolutionImportHandler solutionImportHandler;
  private ImportExportMetrics metrics;

  public ScheduleImportUtil() {
    super();
  }

  public void registerAsHelper() {
    PentahoSystem.get( SolutionImportHandler.class, "solutionImportHandler", null ).addImportHelper( this );
  }

  @Override public void doImport( Object exportArg ) throws ImportException {
    solutionImportHandler = (SolutionImportHandler) exportArg;

    // Initialize metrics for schedule import
    metrics = new ImportExportMetrics( ImportExportMetrics.OperationType.RESTORE );

    List<IJobScheduleRequest> scheduleList = solutionImportHandler.getImportSession().getManifest().getScheduleList();
    if ( solutionImportHandler.isPerformingRestore() ) {
      solutionImportHandler.getLogger().info( Messages.getInstance().getString( "SolutionImportHandler.INFO_START_IMPORT_SCHEDULE" ) );
    }
    if ( CollectionUtils.isNotEmpty( scheduleList ) ) {
      if ( solutionImportHandler.isPerformingRestore() ) {
        solutionImportHandler.getLogger().info( Messages.getInstance().getString( "SolutionImportHandler.INFO_COUNT_SCHEDULUE", scheduleList.size() ) );
      }
      int successfulScheduleImportCount = 0;
      IScheduler scheduler = PentahoSystem.get( IScheduler.class, "IScheduler2", null ); //$NON-NLS-1$
      ISchedulerResource schedulerResource = scheduler.createSchedulerResource();
      if ( solutionImportHandler.isPerformingRestore() ) {
        solutionImportHandler.getLogger().debug( "Pausing the scheduler before the start of the restore process" );
      }
      schedulerResource.pause();
      if ( solutionImportHandler.isPerformingRestore() ) {
        solutionImportHandler.getLogger().debug( "Successfully paused the scheduler" );
      }
      for ( IJobScheduleRequest jobScheduleRequest : scheduleList ) {
        if ( solutionImportHandler.isPerformingRestore() ) {
          solutionImportHandler.getLogger().debug( "Restoring schedule name [ " + jobScheduleRequest.getJobName() + "] inputFile [ " + jobScheduleRequest.getInputFile() + " ] outputFile [ " + jobScheduleRequest.getOutputFile() + "]" );
        }
        
        // ENHANCED: Ensure schedule input file exists before creating schedule
        String inputFilePath = jobScheduleRequest.getInputFile();
        if ( inputFilePath != null && !inputFilePath.trim().isEmpty() ) {
          if ( !ensureScheduleInputFileExists( inputFilePath ) ) {
            metrics.recordSkip( Category.SCHEDULES, jobScheduleRequest.getJobName(), 
                "Input file not found in backup: " + inputFilePath );
            if ( solutionImportHandler.isPerformingRestore() ) {
              solutionImportHandler.getLogger().warn( "Skipping schedule [ " + jobScheduleRequest.getJobName() 
                + " ] because required input file [ " + inputFilePath + " ] could not be imported from backup" );
            }
            continue; // Skip this schedule, the file couldn't be imported
          }
        }
        
        boolean jobExists = false;

        List<IJob> jobs = null;
        try {
          jobs = schedulerResource.getJobsList();
        } catch ( Exception e ) {
          throw new ImportException( "Failed to get list of existing scheduler jobs: " + e.getMessage(), e );
        }
        
        if ( jobs != null ) {

          //paramRequest to map<String, Serializable>
          Map<String, Serializable> mapParamsRequest = new HashMap<>();
          for ( IJobScheduleParam paramRequest : jobScheduleRequest.getJobParameters() ) {
            mapParamsRequest.put( paramRequest.getName(), paramRequest.getValue() );
          }

          // We will check the existing job in the repository. If the job being imported exists, we will remove it from the repository
          for ( IJob job : jobs ) {

            if ( ( mapParamsRequest.get( RESERVEDMAPKEY_LINEAGE_ID ) != null )
              && ( mapParamsRequest.get( RESERVEDMAPKEY_LINEAGE_ID )
              .equals( job.getJobParams().get( RESERVEDMAPKEY_LINEAGE_ID ) ) ) ) {
              jobExists = true;
            }

            if ( solutionImportHandler.isOverwriteFile() && jobExists ) {
              if ( solutionImportHandler.isPerformingRestore() ) {
                solutionImportHandler.getLogger().debug( "Schedule  [ " + jobScheduleRequest.getJobName() + "] already exists and overwrite flag is set to true. Removing the job so we can add it again" );
              }
              IJobRequest jobRequest = scheduler.createJobRequest();
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
                if ( solutionImportHandler.isPerformingRestore() ) {
                  solutionImportHandler.getLogger().debug( "Successfully restored schedule [ " + jobScheduleRequest.getJobName() + " ] " );
                }
                metrics.recordSuccess( Category.SCHEDULES );
                successfulScheduleImportCount++;
              }
            } else {
              metrics.recordFailure( Category.SCHEDULES, jobScheduleRequest.getJobName(), 
                  response.getEntity() != null ? response.getEntity().toString() : "Unknown error" );
              solutionImportHandler.getLogger().error( Messages.getInstance()
                  .getString( "SolutionImportHandler.ERROR_IMPORTING_SCHEDULE", jobScheduleRequest.getJobName(), response.getEntity() != null ? response.getEntity().toString() : "" ) );
            }
          } catch ( Exception e ) {
            // there is a scenario where if the file scheduled has a space in the file name, that it won't work. the di server replaces spaces with underscores and the export mechanism can't determine if it needs this to happen or not
            // so, if we failed to import and there is a space in the path, try again but this time with replacing the space(s)
            if ( jobScheduleRequest.getInputFile().contains( " " ) || jobScheduleRequest.getOutputFile().contains( " " ) ) {
              solutionImportHandler.getLogger().debug( Messages.getInstance()
                  .getString( "SolutionImportHandler.SchedulesWithSpaces", jobScheduleRequest.getInputFile() ) );
              java.io.File inFile = new java.io.File( jobScheduleRequest.getInputFile() );
              java.io.File outFile = new java.io.File( jobScheduleRequest.getOutputFile() );
              String inputFileName = inFile.getParent() + RepositoryFile.SEPARATOR + inFile.getName().replace( " ", "_" );
              String outputFileName = outFile.getParent() + RepositoryFile.SEPARATOR + outFile.getName().replace( " ", "_" );
              jobScheduleRequest.setInputFile( inputFileName );
              jobScheduleRequest.setOutputFile( outputFileName );
              try {
                if ( !java.io.File.separator.equals( RepositoryFile.SEPARATOR ) ) {
                  // on windows systems, the backslashes will result in the file not being found in the repository
                  jobScheduleRequest.setInputFile( inputFileName.replace( java.io.File.separator, RepositoryFile.SEPARATOR ) );
                  jobScheduleRequest
                    .setOutputFile( outputFileName.replace( java.io.File.separator, RepositoryFile.SEPARATOR ) );
                }
                Response response = createSchedulerJob( schedulerResource, jobScheduleRequest );
                if ( response.getStatus() == Response.Status.OK.getStatusCode() ) {
                  if ( response.getEntity() != null ) {
                    // get the schedule job id from the response and add it to the import session
                    ImportSession.getSession().addImportedScheduleJobId( response.getEntity().toString() );
                    metrics.recordSuccess( Category.SCHEDULES );
                    successfulScheduleImportCount++;
                  }
                }
              } catch ( Exception ex ) {
                // log it and keep going. we shouldn't stop processing all schedules just because one fails.
                metrics.recordFailure( Category.SCHEDULES, jobScheduleRequest.getJobName(), ex );
                solutionImportHandler.getLogger().error( Messages.getInstance()
                  .getString( "SolutionImportHandler.ERROR_0001_ERROR_CREATING_SCHEDULE", "[ " + jobScheduleRequest.getJobName() + " ] cause [ " + ex.getMessage() + " ]" ), ex );
              }
            } else {
              // log it and keep going. we shouldn't stop processing all schedules just because one fails.
              metrics.recordFailure( Category.SCHEDULES, jobScheduleRequest.getJobName(), e );
              solutionImportHandler.getLogger().error( Messages.getInstance()
                .getString( "SolutionImportHandler.ERROR_0001_ERROR_CREATING_SCHEDULE", "[ " + jobScheduleRequest.getJobName() + " ]" ) );
            }
          }
        } else {
          solutionImportHandler.getLogger().info( Messages.getInstance()
            .getString( "DefaultImportHandler.ERROR_0009_OVERWRITE_CONTENT", jobScheduleRequest.toString() ) );
        }
      }
      if ( solutionImportHandler.isPerformingRestore() ) {
        solutionImportHandler.getLogger().info( Messages.getInstance()
          .getString( "SolutionImportHandler.INFO_SUCCESSFUL_SCHEDULE_IMPORT_COUNT", successfulScheduleImportCount, scheduleList.size() ) );
      }
      schedulerResource.start();
      if ( solutionImportHandler.isPerformingRestore() ) {
        solutionImportHandler.getLogger().debug( "Successfully started the scheduler" );
      }
    }
    if ( solutionImportHandler.isPerformingRestore() ) {
      solutionImportHandler.getLogger().info( Messages.getInstance().getString( "SolutionImportHandler.INFO_END_IMPORT_SCHEDULE" ) );
    }
    
    // Output comprehensive metrics report
    if ( metrics != null ) {
      solutionImportHandler.getLogger().info( metrics.generateDetailedReport() );
    }
  }

  /**
   * Ensures that the file referenced by a schedule input path exists in the repository.
   * Files are imported by SolutionImportHandler.importRepositoryFilesAndFolders() before
   * this helper runs (since runImportHelpers is called after repository files are imported).
   * 
   * This method checks if the file exists, skipping the schedule if not found.
   * 
   * @param inputFilePath the repository path of the file referenced by the schedule
   * @return true if the file exists, false otherwise
   */
  protected boolean ensureScheduleInputFileExists( String inputFilePath ) {
    if ( inputFilePath == null || inputFilePath.trim().isEmpty() ) {
      return true; // No file reference, nothing to check
    }
    
    // Normalize the path to use forward slashes
    String normalizedPath = inputFilePath.replace( File.separator, RepositoryFile.SEPARATOR );
    
    // Check if the file already exists in the repository
    org.pentaho.platform.api.repository2.unified.IUnifiedRepository repo = 
        PentahoSystem.get( org.pentaho.platform.api.repository2.unified.IUnifiedRepository.class );
    
    if ( repo == null ) {
      logger.warn( "Unable to get repository instance to validate schedule input file" );
      return true; // Assume file exists if we can't check
    }
    
    RepositoryFile existingFile = repo.getFile( normalizedPath );
    if ( existingFile != null ) {
      if ( solutionImportHandler.isPerformingRestore() ) {
        solutionImportHandler.getLogger().debug( "Schedule input file [ " + normalizedPath + " ] already exists in repository" );
      }
      return true;
    }
    
    // File doesn't exist in repository - try to import from the backup bundle
    if ( solutionImportHandler.isPerformingRestore() ) {
      solutionImportHandler.getLogger().debug( "Schedule input file [ " + normalizedPath + " ] not found in repository, attempting to import from backup" );
    }
    
    try {
      // Call SolutionImportHandler to import the file from the backup bundle
      if ( solutionImportHandler.importFileFromBundle( normalizedPath ) ) {
        if ( solutionImportHandler.isPerformingRestore() ) {
          solutionImportHandler.getLogger().debug( "Successfully imported schedule dependency file from backup: [ " + normalizedPath + " ]" );
        }
        return true;
      }
    } catch ( Exception e ) {
      logger.warn( "Error importing schedule dependency file [ " + normalizedPath + " ]: " + e.getMessage() );
    }
    
    // File could not be imported
    if ( solutionImportHandler.isPerformingRestore() ) {
      solutionImportHandler.getLogger().warn( "Schedule input file [ " + normalizedPath + " ] not found in backup and could not be imported" );
    }
    return false;
  }

  /**
   * Normalize a repository path for consistent comparison:
   * - URL decode special characters (%28, %29, %20, etc.)
   * - Convert backslashes to forward slashes
   * - Normalize multiple spaces to single space
   */
  protected String normalizePath( String path ) {
    if ( path == null ) {
      return null;
    }
    
    try {
      // Handle URL encoding: replace + with space first (common in form encoding)
      String processed = path.replace( "+", " " );
      
      // URL decode: convert %XX to actual characters
      String decoded = URLDecoder.decode( processed, "UTF-8" );
      
      // Convert backslashes to forward slashes (Windows path support)
      String normalized = decoded.replace( "\\", "/" );
      
      // Ensure leading slash for repository path
      if ( !normalized.startsWith( "/" ) ) {
        normalized = "/" + normalized;
      }
      
      // Normalize multiple slashes to single slash
      normalized = normalized.replaceAll( "/+", "/" );
      
      // Normalize multiple spaces to single space
      normalized = normalized.replaceAll( " +", " " );
      
      return normalized;
    } catch ( Exception e ) {
      solutionImportHandler.getLogger().debug( "Error normalizing path [ " + path + " ]: " + e.getMessage() );
      return path;
    }
  }

  public Response createSchedulerJob( ISchedulerResource scheduler, IJobScheduleRequest jobScheduleRequest )
    throws IOException {
    Response rs = scheduler != null ? (Response) scheduler.createJob( jobScheduleRequest ) : null;
    if ( jobScheduleRequest.getJobState() != JobState.NORMAL ) {
      try {
        IJobRequest jobRequest = PentahoSystem.get( IScheduler.class, "IScheduler2", null ).createJobRequest();
        jobRequest.setJobId( rs.getEntity().toString() );
        scheduler.pauseJob( jobRequest );
      } catch ( Exception e ) {
        // Job was created but may reference missing files. Log warning but don't fail.
        // The job exists in the scheduler but is paused due to validation issues.
        logger.warn( "Warning: Job created but could not be paused. It may reference missing files: " + e.getMessage() );
      }
    }
    return rs;
  }

  @Override public String getName() {
    return SCHEDULE_IMPORT_UTIL_NAME;
  }
}
