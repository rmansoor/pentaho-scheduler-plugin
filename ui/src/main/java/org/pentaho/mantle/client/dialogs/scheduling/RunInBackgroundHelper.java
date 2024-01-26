package org.pentaho.mantle.client.dialogs.scheduling;

import com.google.gwt.http.client.*;
import com.google.gwt.json.client.*;
import org.pentaho.gwt.widgets.client.dialogs.MessageDialogBox;
import org.pentaho.gwt.widgets.client.utils.NameUtils;
import org.pentaho.gwt.widgets.client.utils.string.StringUtils;
import org.pentaho.mantle.client.environment.EnvironmentHelper;
import org.pentaho.mantle.client.external.services.RunInBackgroundCommandUtils;
import org.pentaho.mantle.client.messages.Messages;

public class RunInBackgroundHelper {

    public static void showDialog( final boolean feedback ) {

        RunInBackgroundCommandUtils( getSolutionPath(), feedback );

        final String filePath = solutionPath;
        String urlPath = NameUtils.URLEncode( NameUtils.encodeRepositoryPath( filePath ) );

        RequestBuilder scheduleFileRequestBuilder = createParametersChecker( urlPath );
        final boolean isXAction = isXAction( urlPath );

        try {
            scheduleFileRequestBuilder.sendRequest( null, new RequestCallback() {
                public void onError(Request request, Throwable exception ) {
                    MessageDialogBox dialogBox =
                            new MessageDialogBox( Messages.getString( "error" ), exception.toString(), false, false, true ); //$NON-NLS-1$
                    dialogBox.center();
                }

                public void onResponseReceived( Request request, Response response ) {
                    if ( response.getStatusCode() == Response.SC_OK ) {
                        String responseMessage = response.getText();
                        boolean hasParams = hasParameters( responseMessage, isXAction );
                        if ( !hasParams ) {
                            setOkButtonText();
                        }
                        centerScheduleOutputLocationDialog();
                    } else {
                        MessageDialogBox dialogBox =
                                new MessageDialogBox(
                                        Messages.getString( "error" ), Messages.getString( "serverErrorColon" ) + " " + response.getStatusCode(), false, false, true ); //$NON-NLS-1$//$NON-NLS-2$//$NON-NLS-3$
                        dialogBox.center();
                    }
                }
            } );
        } catch ( RequestException e ) {
            MessageDialogBox dialogBox =
                    new MessageDialogBox( Messages.getString( "error" ), e.toString(), false, false, true ); //$NON-NLS-1$
            dialogBox.center();
        }
    }

    private static boolean hasParameters( String responseMessage, boolean isXAction ) {
        if ( isXAction ) {
            int numOfInputs = StringUtils.countMatches( responseMessage, "<input" );
            int numOfHiddenInputs = StringUtils.countMatches( responseMessage, "type=\"hidden\"" );
            return numOfInputs - numOfHiddenInputs > 0 ? true : false;
        } else {
            return Boolean.parseBoolean( responseMessage );
        }
    }

    private static boolean isXAction( String urlPath ) {
        if ( ( urlPath != null ) && ( urlPath.endsWith( "xaction" ) ) ) {
            return true;
        } else {
            return false;
        }
    }

    private static RequestBuilder createParametersChecker( String urlPath ) {
        RequestBuilder scheduleFileRequestBuilder = null;
        if ( ( urlPath != null ) && ( urlPath.endsWith( "xaction" ) ) ) {
            scheduleFileRequestBuilder = new RequestBuilder( RequestBuilder.GET, EnvironmentHelper.getFullyQualifiedURL() + "api/repos/" + urlPath
                    + "/parameterUi" );
        } else {
            scheduleFileRequestBuilder = new RequestBuilder( RequestBuilder.GET, EnvironmentHelper.getFullyQualifiedURL() + "api/repo/files/" + urlPath
                    + "/parameterizable" );
        }
        scheduleFileRequestBuilder.setHeader( "accept", "text/plain" ); //$NON-NLS-1$ //$NON-NLS-2$
        scheduleFileRequestBuilder.setHeader( "If-Modified-Since", "01 Jan 1970 00:00:00 GMT" );
        return scheduleFileRequestBuilder;
    }

    public static void checkSchedulePermissionAndDialog(String repositoryFileId ) {
        final String url = ScheduleHelper.getPluginContextURL() + "api/scheduler/isScheduleAllowed?id=" + repositoryFileId; //$NON-NLS-1$
        RequestBuilder requestBuilder = new RequestBuilder( RequestBuilder.GET, url );
        requestBuilder.setHeader( "accept", "text/plain" );
        requestBuilder.setHeader( "If-Modified-Since", "01 Jan 1970 00:00:00 GMT" );
        final MessageDialogBox errorDialog =
                new MessageDialogBox(
                        Messages.getString( "error" ), Messages.getString( "noSchedulePermission" ), false, false, true ); //$NON-NLS-1$ //$NON-NLS-2$
        try {
            requestBuilder.sendRequest( null, new RequestCallback() {

                public void onError( Request request, Throwable caught ) {
                    errorDialog.center();
                }

                public void onResponseReceived( Request request, Response response ) {
                    if ( "true".equalsIgnoreCase( response.getText() ) ) {
                        showDialog( true );
                    } else {
                        errorDialog.center();
                    }
                }
            } );
        } catch ( RequestException re ) {
            errorDialog.center();
        }
    }

    public static void initiateRunInBackground( String repositoryFilePath ) {
        String urlPath = NameUtils.URLEncode( NameUtils.encodeRepositoryPath( repositoryFilePath ) );
        RequestBuilder scheduleFileRequestBuilder = createParametersChecker( urlPath );
        final boolean isXAction = isXAction( urlPath );

        try {
            scheduleFileRequestBuilder.sendRequest( null, new RequestCallback() {

                public void onError( Request request, Throwable exception ) {
                    MessageDialogBox dialogBox =
                            new MessageDialogBox( Messages.getString( "error" ), exception.toString(), false, false, true ); //$NON-NLS-1$
                    dialogBox.center();
                }

                public void onResponseReceived( Request request, Response response ) {
                    if ( response.getStatusCode() == Response.SC_OK ) {
                        final JSONObject scheduleRequest = new JSONObject();
                        scheduleRequest.put( "inputFile", new JSONString( repositoryFilePath ) ); //$NON-NLS-1$

                        //Set date format to append to filename
                        if ( StringUtils.isEmpty( getDateFormat() ) ) {
                            scheduleRequest.put( "appendDateFormat", JSONNull.getInstance() ); //$NON-NLS-1$
                        } else {
                            scheduleRequest.put( "appendDateFormat", new JSONString( getDateFormat() ) ); //$NON-NLS-1$
                        }

                        //Set whether to overwrite the file
                        if ( StringUtils.isEmpty( getOverwriteFile() ) ) {
                            scheduleRequest.put( "overwriteFile", JSONNull.getInstance() ); //$NON-NLS-1$
                        } else {
                            scheduleRequest.put( "overwriteFile", new JSONString( getOverwriteFile() ) ); //$NON-NLS-1$
                        }

                        // Set job name
                        if ( StringUtils.isEmpty( getOutputName() ) ) {
                            scheduleRequest.put( "jobName", JSONNull.getInstance() ); //$NON-NLS-1$
                        } else {
                            scheduleRequest.put( "jobName", new JSONString( getOutputName() ) ); //$NON-NLS-1$
                        }

                        // Set output path location
                        if ( StringUtils.isEmpty( getOutputLocationPath() ) ) {
                            scheduleRequest.put( "outputFile", JSONNull.getInstance() ); //$NON-NLS-1$
                        } else {
                            scheduleRequest.put( "outputFile", new JSONString( getOutputLocationPath() ) ); //$NON-NLS-1$
                        }

                        // BISERVER-9321
                        scheduleRequest.put( "runInBackground", JSONBoolean.getInstance( true ) );

                        String responseMessage = response.getText();
                        final boolean hasParams = hasParameters( responseMessage, isXAction );

                        RequestBuilder emailValidRequest =
                                new RequestBuilder( RequestBuilder.GET, contextURL + "api/emailconfig/isValid" ); //$NON-NLS-1$
                        emailValidRequest.setHeader( "accept", "text/plain" ); //$NON-NLS-1$ //$NON-NLS-2$
                        emailValidRequest.setHeader( "If-Modified-Since", "01 Jan 1970 00:00:00 GMT" );
                        try {
                            emailValidRequest.sendRequest( null, new RequestCallback() {

                                public void onError( Request request, Throwable exception ) {
                                    MessageDialogBox dialogBox =
                                            new MessageDialogBox( Messages.getString( "error" ), exception.toString(), false, false, true ); //$NON-NLS-1$
                                    dialogBox.center();
                                }

                                public void onResponseReceived( Request request, Response response ) {
                                    if ( response.getStatusCode() == Response.SC_OK ) {
                                        // final boolean isEmailConfValid = Boolean.parseBoolean(response.getText());
                                        // force false for now, I have a feeling PM is going to want this, making it easy to turn back
                                        // on
                                        final boolean isEmailConfValid = false;
                                        if ( hasParams ) {
                                            boolean isSchedulesPerspectiveActive = !PerspectiveManager.getInstance().getActivePerspective().getId().equals(PerspectiveManager.SCHEDULES_PERSPECTIVE );
                                            createScheduleParamsDialog( filePath, scheduleRequest, isEmailConfValid, isSchedulesPerspectiveActive );
                                        } else if ( isEmailConfValid ) {
                                            createScheduleEmailDialog( filePath, scheduleRequest );
                                        } else {
                                            // Handle Schedule Parameters
                                            String jsonStringScheduleParams = getScheduleParams( scheduleRequest );
                                            JSONValue scheduleParams = JSONParser.parseStrict( jsonStringScheduleParams );
                                            scheduleRequest.put( "jobParameters", scheduleParams.isArray() );

                                            // just run it
                                            RequestBuilder scheduleFileRequestBuilder =
                                                    new RequestBuilder( RequestBuilder.POST, ScheduleHelper.getPluginContextURL() + "api/scheduler/job" ); //$NON-NLS-1$
                                            scheduleFileRequestBuilder.setHeader( "Content-Type", "application/json" ); //$NON-NLS-1$//$NON-NLS-2$
                                            scheduleFileRequestBuilder.setHeader( "If-Modified-Since", "01 Jan 1970 00:00:00 GMT" );

                                            try {
                                                scheduleFileRequestBuilder.sendRequest( scheduleRequest.toString(), new RequestCallback() {

                                                    @Override
                                                    public void onError( Request request, Throwable exception ) {
                                                        MessageDialogBox dialogBox =
                                                                new MessageDialogBox(
                                                                        Messages.getString( "error" ), exception.toString(), false, false, true ); //$NON-NLS-1$
                                                        dialogBox.center();
                                                    }

                                                    @Override
                                                    public void onResponseReceived( Request request, Response response ) {
                                                        if ( response.getStatusCode() == 200 ) {
                                                            MessageDialogBox dialogBox =
                                                                    new MessageDialogBox(
                                                                            Messages.getString( "runInBackground" ), Messages.getString( "backgroundExecutionStarted" ), //$NON-NLS-1$ //$NON-NLS-2$
                                                                            false, false, true );
                                                            dialogBox.center();
                                                        } else {
                                                            MessageDialogBox dialogBox =
                                                                    new MessageDialogBox(
                                                                            Messages.getString( "error" ), Messages.getString( "serverErrorColon" ) + " " + response.getStatusCode(), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-2$ //$NON-NLS-3$
                                                                            false, false, true );
                                                            dialogBox.center();
                                                        }
                                                    }

                                                } );
                                            } catch ( RequestException e ) {
                                                MessageDialogBox dialogBox = new MessageDialogBox( Messages.getString( "error" ), e.toString(), //$NON-NLS-1$
                                                        false, false, true );
                                                dialogBox.center();
                                            }
                                        }

                                    }
                                }
                            } );
                        } catch ( RequestException e ) {
                            MessageDialogBox dialogBox =
                                    new MessageDialogBox( Messages.getString( "error" ), e.toString(), false, false, true ); //$NON-NLS-1$
                            dialogBox.center();
                        }

                    } else {
                        MessageDialogBox dialogBox =
                                new MessageDialogBox(
                                        Messages.getString( "error" ), Messages.getString( "serverErrorColon" ) + " " + response.getStatusCode(), false, false, true ); //$NON-NLS-1$//$NON-NLS-2$//$NON-NLS-3$
                        dialogBox.center();
                    }
                }

            } );
        } catch ( RequestException e ) {
            MessageDialogBox dialogBox =
                    new MessageDialogBox( Messages.getString( "error" ), e.toString(), false, false, true ); //$NON-NLS-1$
            dialogBox.center();
        }
    }

    private static native void setupNativeHooks( RunInBackgroundCommand cmd )
  /*-{
    $wnd.mantle_runInBackgroundCommand_setOutputName = function(outputName) {
      //CHECKSTYLE IGNORE LineLength FOR NEXT 1 LINES
      cmd.@org.pentaho.mantle.client.commands.RunInBackgroundCommand::setOutputName(Ljava/lang/String;)(outputName);
    }

    $wnd.mantle_runInBackgroundCommand_setOutputLocationPath = function(outputLocationPath) {
      //CHECKSTYLE IGNORE LineLength FOR NEXT 1 LINES
      cmd.@org.pentaho.mantle.client.commands.RunInBackgroundCommand::setOutputLocationPath(Ljava/lang/String;)(outputLocationPath);
    }

    $wnd.mantle_runInBackgroundCommand_setOverwriteFile = function(overwriteFile) {
      //CHECKSTYLE IGNORE LineLength FOR NEXT 1 LINES
      cmd.@org.pentaho.mantle.client.commands.RunInBackgroundCommand::setOverwriteFile(Ljava/lang/String;)(overwriteFile);
    }

    $wnd.mantle_runInBackgroundCommand_setDateFormat = function(dateFormat) {
      //CHECKSTYLE IGNORE LineLength FOR NEXT 1 LINES
      cmd.@org.pentaho.mantle.client.commands.RunInBackgroundCommand::setDateFormat(Ljava/lang/String;)(dateFormat);
    }

    $wnd.mantle_runInBackgroundCommand_performOperation = function(feedback) {
      //CHECKSTYLE IGNORE LineLength FOR NEXT 1 LINES
      cmd.@org.pentaho.mantle.client.commands.RunInBackgroundCommand::performOperation(Z)(feedback);
    }
  }-*/;

    private native void createScheduleOutputLocationDialog(String solutionPath, Boolean feedback) /*-{
   $wnd.pho.createScheduleOutputLocationDialog(solutionPath, feedback);
  }-*/;

    private native void setOkButtonText() /*-{
   $wnd.pho.setOkButtonText();
  }-*/;

    private native void centerScheduleOutputLocationDialog() /*-{
   $wnd.pho.centerScheduleOutputLocationDialog();
  }-*/;

    private native void createScheduleParamsDialog( String filePath, JSONObject scheduleRequest, Boolean isEmailConfigValid, Boolean isSchedulesPerspectiveActive ) /*-{
   $wnd.pho.createScheduleParamsDialog( filePath, scheduleRequest, isEmailConfigValid, isSchedulesPerspectiveActive );
  }-*/;

    private native void createScheduleEmailDialog( String filePath, JSONObject scheduleRequest ) /*-{
   $wnd.pho.createScheduleEmailDialog( filePath, scheduleRequest );
  }-*/;

    private native String getScheduleParams( JSONObject scheduleRequest) /*-{
   return $wnd.pho.getScheduleParams( scheduleRequest );
  }-*/;
}

