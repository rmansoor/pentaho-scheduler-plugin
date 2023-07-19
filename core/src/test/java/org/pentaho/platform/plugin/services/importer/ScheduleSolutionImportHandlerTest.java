/*!
 *
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
 *
 * Copyright (c) 2002-2021 Hitachi Vantara. All rights reserved.
 *
 */

package org.pentaho.platform.plugin.services.importer;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.logging.Log;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentMatcher;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.pentaho.platform.api.engine.IAuthorizationPolicy;
import org.pentaho.platform.api.engine.IPentahoSession;
import org.pentaho.platform.api.engine.security.userroledao.IUserRoleDao;
import org.pentaho.platform.api.mimetype.IMimeType;
import org.pentaho.platform.api.mimetype.IPlatformMimeResolver;
import org.pentaho.platform.api.repository2.unified.IUnifiedRepository;
import org.pentaho.platform.api.repository2.unified.RepositoryFile;
import org.pentaho.platform.api.scheduler2.IScheduler;
import org.pentaho.platform.engine.core.system.PentahoSessionHolder;
import org.pentaho.platform.engine.core.system.PentahoSystem;
import org.pentaho.platform.plugin.services.importexport.ImportSession;
import org.pentaho.platform.plugin.services.importexport.ImportSource;
import org.pentaho.platform.plugin.services.importexport.RepositoryFileBundle;
import org.pentaho.platform.plugin.services.importexport.exportManifest.ExportManifest;
import org.pentaho.platform.plugin.services.importexport.exportManifest.bindings.JobScheduleRequest;
import org.pentaho.platform.security.policy.rolebased.IRoleAuthorizationPolicyRoleBindingDao;
import org.pentaho.platform.web.http.api.resources.SchedulerResource;

import javax.ws.rs.core.Response;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.nullable;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


public class SchedulerSolutionImportHandlerTest {

    private SolutionImportHandler importHandler;

    private IUserRoleDao userRoleDao;
    private IUnifiedRepository repository;
    private IRoleAuthorizationPolicyRoleBindingDao roleAuthorizationPolicyRoleBindingDao;
    private IPlatformMimeResolver mockMimeResolver;

    @Before
    public void setUp() throws Exception {
        userRoleDao = mockToPentahoSystem(IUserRoleDao.class);
        repository = mockToPentahoSystem(IUnifiedRepository.class);
        roleAuthorizationPolicyRoleBindingDao = mockToPentahoSystem(IRoleAuthorizationPolicyRoleBindingDao.class);

        List<IMimeType> mimeTypes = new ArrayList<>();
        try (MockedStatic<PentahoSystem> pentahoSystemMockedStatic = Mockito.mockStatic(PentahoSystem.class)) {
            mockMimeResolver = mock(IPlatformMimeResolver.class);
            pentahoSystemMockedStatic.when(() -> PentahoSystem.get(IPlatformMimeResolver.class))
                    .thenReturn(mockMimeResolver);
            importHandler = spy(new SolutionImportHandler(mimeTypes));
        }

        when(importHandler.getImportSession()).thenReturn(mock(ImportSession.class));
        when(importHandler.getLogger()).thenReturn(mock(Log.class));
    }

    private <T> T mockToPentahoSystem(Class<T> cl) {
        T t = mock(cl);
        PentahoSystem.registerObject(t);
        return t;
    }

    @Test
    public void testImportSchedules() throws Exception {
        List<JobScheduleRequest> schedules = new ArrayList<>();
        JobScheduleRequest scheduleRequest = spy(new JobScheduleRequest());
        schedules.add(scheduleRequest);

        Response response = mock(Response.class);
        when(response.getStatus()).thenReturn(Response.Status.OK.getStatusCode());
        when(response.getEntity()).thenReturn("job id");

        doReturn(response).when(importHandler)
                .createSchedulerJob(any(SchedulerResource.class), eq(scheduleRequest));

        try (MockedStatic<PentahoSystem> pentahoSystemMockedStatic = Mockito.mockStatic(PentahoSystem.class);
             MockedStatic<PentahoSessionHolder> pentahoSessionHolderMockedStatic = Mockito.mockStatic(PentahoSessionHolder.class)) {
            IAuthorizationPolicy iAuthorizationPolicyMock = mock(IAuthorizationPolicy.class);
            IScheduler iSchedulerMock = mock(IScheduler.class);
            pentahoSystemMockedStatic.when(() -> PentahoSystem.get(eq(IAuthorizationPolicy.class)))
                    .thenReturn(iAuthorizationPolicyMock);
            pentahoSystemMockedStatic.when(() -> PentahoSystem.get(eq(IScheduler.class), anyString(), eq(null)))
                    .thenReturn(iSchedulerMock);
            when(iSchedulerMock.getStatus()).thenReturn(mock(IScheduler.SchedulerStatus.class));
            pentahoSessionHolderMockedStatic.when(PentahoSessionHolder::getSession)
                    .thenReturn(mock(IPentahoSession.class));

            importHandler.importSchedules(schedules);

            verify(importHandler)
                    .createSchedulerJob(any(SchedulerResource.class), eq(scheduleRequest));
            Assert.assertEquals(1, ImportSession.getSession().getImportedScheduleJobIds().size());
        }
    }

    @Test
    public void testImportSchedules_FailsToCreateSchedule() throws Exception {
        List<JobScheduleRequest> schedules = new ArrayList<>();
        JobScheduleRequest scheduleRequest = spy(new JobScheduleRequest());
        scheduleRequest.setInputFile("/home/admin/scheduledTransform.ktr");
        scheduleRequest.setOutputFile("/home/admin/scheduledTransform*");
        schedules.add(scheduleRequest);

        doThrow(new IOException("error creating schedule")).when(importHandler).createSchedulerJob(
                any(SchedulerResource.class), eq(scheduleRequest));

        try (MockedStatic<PentahoSystem> pentahoSystemMockedStatic = Mockito.mockStatic(PentahoSystem.class);
             MockedStatic<PentahoSessionHolder> pentahoSessionHolderMockedStatic = Mockito.mockStatic(PentahoSessionHolder.class)) {
            IAuthorizationPolicy iAuthorizationPolicyMock = mock(IAuthorizationPolicy.class);
            IScheduler iSchedulerMock = mock(IScheduler.class);
            pentahoSystemMockedStatic.when(() -> PentahoSystem.get(eq(IAuthorizationPolicy.class)))
                    .thenReturn(iAuthorizationPolicyMock);
            pentahoSystemMockedStatic.when(() -> PentahoSystem.get(eq(IScheduler.class), anyString(), eq(null)))
                    .thenReturn(iSchedulerMock);
            when(iSchedulerMock.getStatus()).thenReturn(mock(IScheduler.SchedulerStatus.class));
            pentahoSessionHolderMockedStatic.when(PentahoSessionHolder::getSession)
                    .thenReturn(mock(IPentahoSession.class));

            importHandler.importSchedules(schedules);
            Assert.assertEquals(0, ImportSession.getSession().getImportedScheduleJobIds().size());
        }
    }

    @Test
    public void testImportSchedules_FailsToCreateScheduleWithSpace() throws Exception {
        List<JobScheduleRequest> schedules = new ArrayList<>();
        JobScheduleRequest scheduleRequest = spy(new JobScheduleRequest());
        scheduleRequest.setInputFile("/home/admin/scheduled Transform.ktr");
        scheduleRequest.setOutputFile("/home/admin/scheduled Transform*");
        schedules.add(scheduleRequest);

        ScheduleRequestMatcher throwMatcher =
                new ScheduleRequestMatcher("/home/admin/scheduled Transform.ktr", "/home/admin/scheduled Transform*");
        doThrow(new IOException("error creating schedule")).when(importHandler).createSchedulerJob(
                any(SchedulerResource.class), argThat(throwMatcher));

        Response response = mock(Response.class);
        when(response.getStatus()).thenReturn(Response.Status.OK.getStatusCode());
        when(response.getEntity()).thenReturn("job id");
        ScheduleRequestMatcher goodMatcher =
                new ScheduleRequestMatcher("/home/admin/scheduled_Transform.ktr", "/home/admin/scheduled_Transform*");
        doReturn(response).when(importHandler).createSchedulerJob(any(SchedulerResource.class),
                argThat(goodMatcher));

        try (MockedStatic<PentahoSystem> pentahoSystemMockedStatic = Mockito.mockStatic(PentahoSystem.class);
             MockedStatic<PentahoSessionHolder> pentahoSessionHolderMockedStatic = Mockito.mockStatic(PentahoSessionHolder.class)) {
            IAuthorizationPolicy iAuthorizationPolicyMock = mock(IAuthorizationPolicy.class);
            IScheduler iSchedulerMock = mock(IScheduler.class);
            pentahoSystemMockedStatic.when(() -> PentahoSystem.get(eq(IAuthorizationPolicy.class))).thenReturn(iAuthorizationPolicyMock);
            pentahoSystemMockedStatic.when(() -> PentahoSystem.get(eq(IScheduler.class), anyString(), eq(null)))
                    .thenReturn(iSchedulerMock);
            when(iSchedulerMock.getStatus()).thenReturn(mock(IScheduler.SchedulerStatus.class));
            pentahoSessionHolderMockedStatic.when(PentahoSessionHolder::getSession).thenReturn(mock(IPentahoSession.class));
            importHandler.importSchedules(schedules);
            verify(importHandler, times(2)).createSchedulerJob(
                    any(SchedulerResource.class), any(JobScheduleRequest.class));
            Assert.assertEquals(1, ImportSession.getSession().getImportedScheduleJobIds().size());
        }
    }

    @Test
    public void testImportSchedules_FailsToCreateScheduleWithSpaceOnWindows() throws Exception {
        String sep = File.separator;
        System.setProperty("file.separator", "\\");
        List<JobScheduleRequest> schedules = new ArrayList<>();
        JobScheduleRequest scheduleRequest = spy(new JobScheduleRequest());
        scheduleRequest.setInputFile("/home/admin/scheduled Transform.ktr");
        scheduleRequest.setOutputFile("/home/admin/scheduled Transform*");
        schedules.add(scheduleRequest);

        ScheduleRequestMatcher throwMatcher =
                new ScheduleRequestMatcher("/home/admin/scheduled Transform.ktr", "/home/admin/scheduled Transform*");
        doThrow(new IOException("error creating schedule")).when(importHandler).createSchedulerJob(
                nullable(SchedulerResource.class), argThat(throwMatcher));

        Response response = mock(Response.class);
        when(response.getStatus()).thenReturn(Response.Status.OK.getStatusCode());
        when(response.getEntity()).thenReturn("job id");
        ScheduleRequestMatcher goodMatcher =
                new ScheduleRequestMatcher("/home/admin/scheduled_Transform.ktr", "/home/admin/scheduled_Transform*");
        doReturn(response).when(importHandler).createSchedulerJob(nullable(SchedulerResource.class),
                argThat(goodMatcher));

        try (MockedStatic<PentahoSystem> pentahoSystemMockedStatic = Mockito.mockStatic(PentahoSystem.class);
             MockedStatic<PentahoSessionHolder> pentahoSessionHolderMockedStatic = Mockito.mockStatic(PentahoSessionHolder.class)) {
            IAuthorizationPolicy iAuthorizationPolicyMock = mock(IAuthorizationPolicy.class);
            IScheduler iSchedulerMock = mock(IScheduler.class);
            pentahoSystemMockedStatic.when(() -> PentahoSystem.get(eq(IAuthorizationPolicy.class)))
                    .thenReturn(iAuthorizationPolicyMock);
            pentahoSystemMockedStatic.when(() -> PentahoSystem.get(eq(IScheduler.class), anyString(), eq(null)))
                    .thenReturn(iSchedulerMock);
            when(iSchedulerMock.getStatus()).thenReturn(mock(IScheduler.SchedulerStatus.class));
            pentahoSessionHolderMockedStatic.when(PentahoSessionHolder::getSession)
                    .thenReturn(mock(IPentahoSession.class));

            importHandler.importSchedules(schedules);
            verify(importHandler, times(2))
                    .createSchedulerJob(nullable(SchedulerResource.class), nullable(JobScheduleRequest.class));
            Assert.assertEquals(1, ImportSession.getSession().getImportedScheduleJobIds().size());
            System.setProperty("file.separator", sep);
        }
    }
}

private static class ScheduleRequestMatcher implements ArgumentMatcher<JobScheduleRequest> {
    private final String input;
    private final String output;

    public ScheduleRequestMatcher( String input, String output ) {
        this.input = input;
        this.output = output;
    }

    @Override public boolean matches( JobScheduleRequest jsr ) {
        boolean matchedInput = input.equals( FilenameUtils.separatorsToUnix( jsr.getInputFile() ) );
        boolean matchedOutput = output.equals( FilenameUtils.separatorsToUnix( jsr.getOutputFile() ) );
        return matchedInput && matchedOutput;
    }
}

    @Test
    public void testGetFile() {
        RepositoryFileImportBundle importBundle = new RepositoryFileImportBundle();
        importBundle.setPath( "/BASE_PATH/" );

        RepositoryFile repoFile = new RepositoryFile.Builder( "FILE_NAME" ).build();
        ImportSource.IRepositoryFileBundle fileBundle = new RepositoryFileBundle( repoFile, null, "parentDir", null, "UTF-8", null );
        fileBundle.setPath( "SUB_PATH/" );

        RepositoryFile expectedFile = new RepositoryFile.Builder( "EXPECTED_FILE" ).build();
        when( repository.getFile( "/BASE_PATH/SUB_PATH/FILE_NAME" ) ).thenReturn( expectedFile );
    }

    @Test
    public void testIsFileHidden() {
        IMimeType hiddenMime = mock( IMimeType.class );
        IMimeType visibleMime = mock( IMimeType.class );
        when( hiddenMime.isHidden() ).thenReturn( true );
        when( visibleMime.isHidden() ).thenReturn( false );
        ImportSession.ManifestFile manifestFile = mock( ImportSession.ManifestFile.class );
        RepositoryFile repoFile = new RepositoryFile.Builder( "FILE_NAME" ).hidden( true ).build();

        when( manifestFile.isFileHidden() ).thenReturn( true );
        Assert.assertTrue( importHandler.isFileHidden( repoFile, manifestFile, "SOURCE_PATH" ) );

        when( manifestFile.isFileHidden() ).thenReturn( false );
        Assert.assertFalse( importHandler.isFileHidden( repoFile, manifestFile, "SOURCE_PATH" ) );

        when( manifestFile.isFileHidden() ).thenReturn( null );
        Assert.assertTrue( importHandler.isFileHidden( repoFile, manifestFile, "SOURCE_PATH" ) );

        repoFile = new RepositoryFile.Builder( "FILE_NAME" ).hidden( false ).build();
        Assert.assertFalse( importHandler.isFileHidden( repoFile, manifestFile, "SOURCE_PATH" ) );

        when( mockMimeResolver.resolveMimeTypeForFileName( "SOURCE_PATH" ) ).thenReturn( hiddenMime );
        Assert.assertTrue( importHandler.isFileHidden( null, manifestFile, "SOURCE_PATH" ) );

        when( mockMimeResolver.resolveMimeTypeForFileName( "SOURCE_PATH" ) ).thenReturn( visibleMime );
        Assert.assertEquals( RepositoryFile.HIDDEN_BY_DEFAULT, importHandler.isFileHidden( null, manifestFile, "SOURCE_PATH" ) );
    }

    @Test
    public void testIsSchedulable() {
        ImportSession.ManifestFile manifestFile = mock( ImportSession.ManifestFile.class );
        RepositoryFile repoFile = new RepositoryFile.Builder( "FILE_NAME" ).schedulable( true ).build();

        when( manifestFile.isFileSchedulable() ).thenReturn( true );
        Assert.assertTrue( importHandler.isSchedulable( repoFile, manifestFile ) );

        when( manifestFile.isFileSchedulable() ).thenReturn( false );
        Assert.assertFalse( importHandler.isSchedulable( repoFile, manifestFile ) );

        when( manifestFile.isFileSchedulable() ).thenReturn( null );
        Assert.assertTrue( importHandler.isSchedulable( repoFile, manifestFile ) );

        Assert.assertEquals( RepositoryFile.SCHEDULABLE_BY_DEFAULT, importHandler.isSchedulable( null, manifestFile ) );
    }

    @Test
    public void testFileIsScheduleInputSource() {
        ExportManifest manifest = mock( ExportManifest.class );
        List<JobScheduleRequest> scheduleRequests = new ArrayList<>();
        for ( int i = 0; i < 10; i++ ) {
            JobScheduleRequest jobScheduleRequest = new JobScheduleRequest();
            jobScheduleRequest.setInputFile( "/public/test/file" + i );
            scheduleRequests.add( jobScheduleRequest );
        }
        Assert.assertFalse( importHandler.fileIsScheduleInputSource( manifest, null ) );

        when( manifest.getScheduleList() ).thenReturn( scheduleRequests );

        Assert.assertFalse( importHandler.fileIsScheduleInputSource( manifest, "/public/file" ) );
        Assert.assertTrue( importHandler.fileIsScheduleInputSource( manifest, "/public/test/file3" ) );
        Assert.assertTrue( importHandler.fileIsScheduleInputSource( manifest, "public/test/file3" ) );
    }
    @After
    public void tearDown() throws Exception {
        ImportSession.getSession().getImportedScheduleJobIds().clear();
        PentahoSystem.clearObjectFactory();
    }
}
