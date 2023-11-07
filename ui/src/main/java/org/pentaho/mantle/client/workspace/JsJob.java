/*!
 * This program is free software; you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License, version 2.1 as published by the Free Software
 * Foundation.
 *
 * You should have received a copy of the GNU Lesser General Public License along with this
 * program; if not, you can obtain a copy at http://www.gnu.org/licenses/old-licenses/lgpl-2.1.html
 * or from the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * Copyright (c) 2002-2018 Hitachi Vantara..  All rights reserved.
 */

package org.pentaho.mantle.client.workspace;

import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.core.client.JsArray;
import com.google.gwt.i18n.client.DateTimeFormat;
import com.google.gwt.i18n.client.DateTimeFormat.PredefinedFormat;
import com.google.gwt.json.client.JSONObject;
import com.google.gwt.json.client.JSONString;
import com.google.gwt.json.client.JSONValue;

import java.util.Date;

public class JsJob extends JavaScriptObject {

  // Overlay types always have protected, zero argument constructors.
  protected JsJob() {
  }

  // JSNI methods to get job data.
  public final native String getJobId() /*-{ return this.jobId; }-*/; //

  public final native String getJobName() /*-{ return this.jobName; }-*/; //

  public final native String getUserName() /*-{ return this.userName; }-*/; //

  private final native String getNativeNextRun() /*-{ return this.nextRun; }-*/; //

  private final native String getNativeLastRun() /*-{ return this.lastRun; }-*/; //

  public final native JSONObject getJobParams() /*-{ return this.jobParams; }-*/; //

  public final native JsJobTrigger getJobTrigger() /*-{ return this.jobTrigger; }-*/; //

  public final native String getState() /*-{ return this.state; }-*/; //

  public final native void setState( String newState ) /*-{ this.state = newState; }-*/; //

  public final String getJobParamValue( String name ) {
    JSONObject jobParams = getJobParams();
    if ( jobParams.containsKey( name ) ) {
      return jobParams.get( name ).toString();
    }
    return null;
  }

  public final JSONObject getJobParam(String name ) {
    JSONObject jobParams = getJobParams();
    if ( jobParams.containsKey( name  ) ) {
      return jobParams.get( name ).isObject();
    }
    return null;
  }


  public final boolean hasResourceName() {
    String resource = getJobParamValue( "ActionAdapterQuartzJob-StreamProvider" );
    return ( resource != null && !"".equals( resource ) );
  }

  public final String getFullResourceName() {
    String resource = getJobParamValue( "ActionAdapterQuartzJob-StreamProvider" );
    if ( resource == null || "".equals( resource ) ) {
      return getJobName();
    }

    int outputFileIndex = resource.indexOf( ":outputFile = /" );
    return resource.substring( resource.indexOf( "/" ),
            ( outputFileIndex != -1 ) ? outputFileIndex : resource.indexOf( ":" ) );
  }

  public final String getOutputPath() {
    String resource = getJobParamValue( "ActionAdapterQuartzJob-StreamProvider" );
    if ( resource == null || "".equals( resource ) ) {
      return "";
    }
    resource = resource.substring( resource.indexOf( ":" ) );
    resource = resource.substring( resource.indexOf( "/" ), resource.lastIndexOf( "/" ) );
    return resource;
  }

  public final void setOutputPath( String outputPath, String outputFileName ) {
    getJobParams().put("ActionAdapterQuartzJob-StreamProvider", new JSONString("input file = " + getFullResourceName() + ":outputFile = " + outputPath + "/" + outputFileName
            + ".*" ));
  }

  public final String getShortResourceName() {
    String resource = getFullResourceName();
    if ( resource.indexOf( "/" ) != -1 ) {
      resource = resource.substring( resource.lastIndexOf( "/" ) + 1 );
    }
    return resource;
  }


  public final Date getLastRun() {
    return formatDate( getNativeLastRun() );
  }

  public final Date getNextRun() {
    return formatDate( getNativeNextRun() );
  }

  public static Date formatDate( String dateStr ) {
    try {
      DateTimeFormat format = DateTimeFormat.getFormat( PredefinedFormat.ISO_8601 );
      return format.parse( dateStr );
    } catch ( Throwable t ) {
      //ignored
    }

    try {
      DateTimeFormat format = DateTimeFormat.getFormat( "yyyy-MM-dd'T'HH:mm:ssZZZ" );
      return format.parse( dateStr );
    } catch ( Throwable t ) {
      //ignored
    }

    return null;
  }

  public final native void setJobTrigger( JsJobTrigger trigger ) /*-{ this.jobTrigger = trigger; }-*/;

  public final native String setJobName( String name ) /*-{ this.jobName = name; }-*/; //

}
