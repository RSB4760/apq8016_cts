/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.telecom.cts;

import static android.telecom.cts.TestUtils.*;

import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.telecom.Call;
import android.telecom.Connection;
import android.telecom.ConnectionRequest;
import android.telecom.DisconnectCause;
import android.telecom.PhoneAccountHandle;
import android.telecom.RemoteConnection;
import android.telecom.RemoteConnection.VideoProvider;
import android.telecom.StatusHints;
import android.telecom.TelecomManager;
import android.telecom.VideoProfile;


import java.util.ArrayList;
import java.util.List;

/**
 * Extended suite of tests that use {@link CtsConnectionService} and {@link MockInCallService} to
 * verify the functionality of Remote Connections.
 * We make 2 connections on the {@link CtsConnectionService} & we create 2 connections on the
 * {@link CtsRemoteConnectionService} via the {@link RemoteConnection} object. We store this
 * corresponding RemoteConnection object on the connections to plumb the modifications on
 * the connections in {@link CtsConnectionService} to the connections on
 * {@link CtsRemoteConnectionService}.
 */
public class RemoteConnectionTest extends BaseRemoteTelecomTest {

    MockConnection mConnection;
    MockConnection mRemoteConnection;
    RemoteConnection mRemoteConnectionObject;

    public void testRemoteConnectionOutgoingCall() {
        if (!shouldTestTelecom(mContext)) {
            return;
        }
        addRemoteConnectionOutgoingCall();
        final Call call = mInCallCallbacks.getService().getLastCall();
        assertCallState(call, Call.STATE_DIALING);

        verifyRemoteConnectionObject(mRemoteConnectionObject, mRemoteConnection);

        mConnection.setActive();
        mRemoteConnection.setActive();

        assertCallState(call, Call.STATE_ACTIVE);
        assertConnectionState(mConnection, Connection.STATE_ACTIVE);
        assertRemoteConnectionState(mRemoteConnectionObject, Connection.STATE_ACTIVE);
        assertConnectionState(mRemoteConnection, Connection.STATE_ACTIVE);

        call.hold();
        assertCallState(call, Call.STATE_HOLDING);
        assertConnectionState(mConnection, Connection.STATE_HOLDING);
        assertRemoteConnectionState(mRemoteConnectionObject, Connection.STATE_HOLDING);
        assertConnectionState(mRemoteConnection, Connection.STATE_HOLDING);

        call.unhold();
        assertCallState(call, Call.STATE_ACTIVE);
        assertConnectionState(mConnection, Connection.STATE_ACTIVE);
        assertRemoteConnectionState(mRemoteConnectionObject, Connection.STATE_ACTIVE);
        assertConnectionState(mRemoteConnection, Connection.STATE_ACTIVE);

        call.disconnect();
        assertCallState(call, Call.STATE_DISCONNECTED);
        assertConnectionState(mConnection, Connection.STATE_DISCONNECTED);
        assertRemoteConnectionState(mRemoteConnectionObject, Connection.STATE_DISCONNECTED);
        assertConnectionState(mRemoteConnection, Connection.STATE_DISCONNECTED);
    }

    public void testRemoteConnectionIncomingCallAccept() {
        if (!shouldTestTelecom(mContext)) {
            return;
        }
        addRemoteConnectionIncomingCall();
        final Call call = mInCallCallbacks.getService().getLastCall();
        assertCallState(call, Call.STATE_RINGING);

        verifyRemoteConnectionObject(mRemoteConnectionObject, mRemoteConnection);

        assertConnectionState(mConnection, Connection.STATE_RINGING);
        assertRemoteConnectionState(mRemoteConnectionObject, Connection.STATE_RINGING);
        assertConnectionState(mRemoteConnection, Connection.STATE_RINGING);

        call.answer(VideoProfile.STATE_AUDIO_ONLY);
        assertCallState(call, Call.STATE_ACTIVE);
        assertConnectionState(mConnection, Connection.STATE_ACTIVE);
        assertRemoteConnectionState(mRemoteConnectionObject, Connection.STATE_ACTIVE);
        assertConnectionState(mRemoteConnection, Connection.STATE_ACTIVE);
    }

    public void testRemoteConnectionIncomingCallReject() {
        if (!shouldTestTelecom(mContext)) {
            return;
        }
        addRemoteConnectionIncomingCall();
        final Call call = mInCallCallbacks.getService().getLastCall();
        assertCallState(call, Call.STATE_RINGING);

        verifyRemoteConnectionObject(mRemoteConnectionObject, mRemoteConnection);

        assertConnectionState(mConnection, Connection.STATE_RINGING);
        assertRemoteConnectionState(mRemoteConnectionObject, Connection.STATE_RINGING);
        assertConnectionState(mRemoteConnection, Connection.STATE_RINGING);

        call.reject(false, null);
        assertCallState(call, Call.STATE_DISCONNECTED);
        assertConnectionState(mConnection, Connection.STATE_DISCONNECTED);
        assertRemoteConnectionState(mRemoteConnectionObject, Connection.STATE_DISCONNECTED);
        assertConnectionState(mRemoteConnection, Connection.STATE_DISCONNECTED);
    }

    public void testRemoteConnectionDTMFTone() {
        if (!shouldTestTelecom(mContext)) {
            return;
        }
        addRemoteConnectionIncomingCall();
        final Call call = mInCallCallbacks.getService().getLastCall();
        assertCallState(call, Call.STATE_RINGING);

        verifyRemoteConnectionObject(mRemoteConnectionObject, mRemoteConnection);

        assertTrue(mConnection.getDtmfString().isEmpty());
        assertTrue(mRemoteConnection.getDtmfString().isEmpty());
        call.playDtmfTone('1');
        assertDtmfString(mConnection, "1");
        assertDtmfString(mRemoteConnection, "1");
        call.stopDtmfTone();
        assertDtmfString(mConnection, "1.");
        assertDtmfString(mRemoteConnection, "1.");
        call.playDtmfTone('3');
        assertDtmfString(mConnection, "1.3");
        assertDtmfString(mRemoteConnection, "1.3");
        call.stopDtmfTone();
        assertDtmfString(mConnection, "1.3.");
        assertDtmfString(mRemoteConnection, "1.3.");
    }

    public void testRemoteConnectionCallbacks_StateChange() {
        if (!shouldTestTelecom(mContext)) {
            return;
        }

        Handler handler = setupRemoteConnectionCallbacksTest();

        final InvokeCounter callbackInvoker =
                new InvokeCounter("testRemoteConnectionCallbacks_StateChange");
        RemoteConnection.Callback callback;

        callback = new RemoteConnection.Callback() {
            @Override
            public void onStateChanged(RemoteConnection connection, int state) {
                callbackInvoker.invoke(connection, state);
            }
        };
        mRemoteConnectionObject.registerCallback(callback, handler);
        mRemoteConnection.setActive();
        callbackInvoker.waitForCount(1, WAIT_FOR_STATE_CHANGE_TIMEOUT_MS);
        assertEquals(mRemoteConnectionObject, callbackInvoker.getArgs(0)[0]);
        assertEquals(Connection.STATE_ACTIVE, callbackInvoker.getArgs(0)[1]);
        mRemoteConnectionObject.unregisterCallback(callback);
    }

    public void testRemoteConnectionCallbacks_RingbackRequest() {
        if (!shouldTestTelecom(mContext)) {
            return;
        }

        Handler handler = setupRemoteConnectionCallbacksTest();

        final InvokeCounter callbackInvoker =
                new InvokeCounter("testRemoteConnectionCallbacks_RingbackRequest");
        RemoteConnection.Callback callback;

        callback = new RemoteConnection.Callback() {
            @Override
            public void onRingbackRequested(RemoteConnection connection, boolean ringback) {
                callbackInvoker.invoke(connection, ringback);
            }
        };
        mRemoteConnectionObject.registerCallback(callback, handler);
        mRemoteConnection.setRingbackRequested(true);
        callbackInvoker.waitForCount(1, WAIT_FOR_STATE_CHANGE_TIMEOUT_MS);
        assertEquals(mRemoteConnectionObject, callbackInvoker.getArgs(0)[0]);
        assertTrue((boolean) callbackInvoker.getArgs(0)[1]);
        mRemoteConnectionObject.unregisterCallback(callback);
    }

    public void testRemoteConnectionCallbacks_ConnectionCapabilities() {
        if (!shouldTestTelecom(mContext)) {
            return;
        }

        Handler handler = setupRemoteConnectionCallbacksTest();

        final InvokeCounter callbackInvoker =
                new InvokeCounter("testRemoteConnectionCallbacks_ConnectionCapabilities");
        RemoteConnection.Callback callback;

        callback = new RemoteConnection.Callback() {
            @Override
            public void onConnectionCapabilitiesChanged(
                    RemoteConnection connection,
                    int connectionCapabilities) {
                callbackInvoker.invoke(connection, connectionCapabilities);
            }
        };
        mRemoteConnectionObject.registerCallback(callback, handler);
        int capabilities = mRemoteConnection.getConnectionCapabilities() | Connection.CAPABILITY_MUTE;
        mRemoteConnection.setConnectionCapabilities(capabilities);
        mRemoteConnection.setRingbackRequested(true);
        callbackInvoker.waitForCount(1, WAIT_FOR_STATE_CHANGE_TIMEOUT_MS);
        assertEquals(mRemoteConnectionObject, callbackInvoker.getArgs(0)[0]);
        assertEquals(capabilities, callbackInvoker.getArgs(0)[1]);
        mRemoteConnectionObject.unregisterCallback(callback);

    }

    public void testRemoteConnectionCallbacks_PostDialWait() {
        if (!shouldTestTelecom(mContext)) {
            return;
        }

        Handler handler = setupRemoteConnectionCallbacksTest();

        final InvokeCounter callbackInvoker =
                new InvokeCounter("testRemoteConnectionCallbacks_PostDialWait");
        RemoteConnection.Callback callback;

        callback = new RemoteConnection.Callback() {
            @Override
            public void onPostDialWait(RemoteConnection connection,
                                       String remainingPostDialSequence) {
                callbackInvoker.invoke(connection, remainingPostDialSequence);
            }
        };
        mRemoteConnectionObject.registerCallback(callback, handler);
        String postDialSequence = "test";
        mRemoteConnection.setPostDialWait(postDialSequence);
        callbackInvoker.waitForCount(1, WAIT_FOR_STATE_CHANGE_TIMEOUT_MS);
        assertEquals(mRemoteConnectionObject, callbackInvoker.getArgs(0)[0]);
        assertEquals(postDialSequence, callbackInvoker.getArgs(0)[1]);
        mRemoteConnectionObject.unregisterCallback(callback);
    }

    public void testRemoteConnectionCallbacks_PostDialChar() {
        if (!shouldTestTelecom(mContext)) {
            return;
        }

        Handler handler = setupRemoteConnectionCallbacksTest();

        final InvokeCounter callbackInvoker =
                new InvokeCounter("testRemoteConnectionCallbacks_PostDialChar");
        RemoteConnection.Callback callback;

        callback = new RemoteConnection.Callback() {
            @Override
            public void onPostDialChar(RemoteConnection connection, char nextChar) {
                callbackInvoker.invoke(connection, nextChar);
            }
        };
        mRemoteConnectionObject.registerCallback(callback, handler);
        char postDialChar = '3';
        mRemoteConnection.setNextPostDialChar(postDialChar);
        callbackInvoker.waitForCount(1, WAIT_FOR_STATE_CHANGE_TIMEOUT_MS);
        assertEquals(mRemoteConnectionObject, callbackInvoker.getArgs(0)[0]);
        assertEquals(postDialChar, callbackInvoker.getArgs(0)[1]);
        mRemoteConnectionObject.unregisterCallback(callback);
    }

    public void testRemoteConnectionCallbacks_VoipAudio() {
        if (!shouldTestTelecom(mContext)) {
            return;
        }

        Handler handler = setupRemoteConnectionCallbacksTest();

        final InvokeCounter callbackInvoker =
                new InvokeCounter("testRemoteConnectionCallbacks_VoipAudio");
        RemoteConnection.Callback callback;

        callback = new RemoteConnection.Callback() {
            @Override
            public void onVoipAudioChanged(RemoteConnection connection, boolean isVoip) {
                callbackInvoker.invoke(connection, isVoip);
            }
        };
        mRemoteConnectionObject.registerCallback(callback, handler);
        mRemoteConnection.setAudioModeIsVoip(true);
        callbackInvoker.waitForCount(1, WAIT_FOR_STATE_CHANGE_TIMEOUT_MS);
        assertEquals(mRemoteConnectionObject, callbackInvoker.getArgs(0)[0]);
        assertTrue((boolean) callbackInvoker.getArgs(0)[1]);
        mRemoteConnectionObject.unregisterCallback(callback);
    }

    public void testRemoteConnectionCallbacks_StatusHints() {
        if (!shouldTestTelecom(mContext)) {
            return;
        }

        Handler handler = setupRemoteConnectionCallbacksTest();

        final InvokeCounter callbackInvoker =
                new InvokeCounter("testRemoteConnectionCallbacks_StatusHints");
        RemoteConnection.Callback callback;

        callback = new RemoteConnection.Callback() {
            @Override
            public void onStatusHintsChanged(RemoteConnection connection, StatusHints statusHints) {
                callbackInvoker.invoke(connection, statusHints);
            }
        };
        mRemoteConnectionObject.registerCallback(callback, handler);
        StatusHints hints = new StatusHints("test", null, null);
        mRemoteConnection.setStatusHints(hints);
        callbackInvoker.waitForCount(1, WAIT_FOR_STATE_CHANGE_TIMEOUT_MS);
        assertEquals(mRemoteConnectionObject, callbackInvoker.getArgs(0)[0]);
        assertEquals(hints, callbackInvoker.getArgs(0)[1]);
        mRemoteConnectionObject.unregisterCallback(callback);
    }

    public void testRemoteConnectionCallbacks_AddressChange() {
        if (!shouldTestTelecom(mContext)) {
            return;
        }

        Handler handler = setupRemoteConnectionCallbacksTest();

        final InvokeCounter callbackInvoker =
                new InvokeCounter("testRemoteConnectionCallbacks_AddressChange");
        RemoteConnection.Callback callback;

        callback = new RemoteConnection.Callback() {
            @Override
            public void onAddressChanged(RemoteConnection connection, Uri address,
                                         int presentation) {
                callbackInvoker.invoke(connection, address, presentation);
            }
        };
        mRemoteConnectionObject.registerCallback(callback, handler);
        Uri address = Uri.parse("tel:555");
        mRemoteConnection.setAddress(address, TelecomManager.PRESENTATION_ALLOWED);
        callbackInvoker.waitForCount(1, WAIT_FOR_STATE_CHANGE_TIMEOUT_MS);
        assertEquals(mRemoteConnectionObject, callbackInvoker.getArgs(0)[0]);
        assertEquals(address, callbackInvoker.getArgs(0)[1]);
        assertEquals(TelecomManager.PRESENTATION_ALLOWED, callbackInvoker.getArgs(0)[2]);
        mRemoteConnectionObject.unregisterCallback(callback);
    }

    public void testRemoteConnectionCallbacks_CallerDisplayName() {
        if (!shouldTestTelecom(mContext)) {
            return;
        }

        Handler handler = setupRemoteConnectionCallbacksTest();

        final InvokeCounter callbackInvoker =
                new InvokeCounter("testRemoteConnectionCallbacks_CallerDisplayName");
        RemoteConnection.Callback callback;

        callback = new RemoteConnection.Callback() {
            @Override
            public void onCallerDisplayNameChanged(
                    RemoteConnection connection, String callerDisplayName, int presentation) {
                callbackInvoker.invoke(connection, callerDisplayName, presentation);
            }
        };
        mRemoteConnectionObject.registerCallback(callback, handler);
        String callerDisplayName = "test";
        mRemoteConnection.setCallerDisplayName(callerDisplayName, TelecomManager.PRESENTATION_ALLOWED);
        callbackInvoker.waitForCount(1, WAIT_FOR_STATE_CHANGE_TIMEOUT_MS);
        assertEquals(mRemoteConnectionObject, callbackInvoker.getArgs(0)[0]);
        assertEquals(callerDisplayName, callbackInvoker.getArgs(0)[1]);
        assertEquals(TelecomManager.PRESENTATION_ALLOWED, callbackInvoker.getArgs(0)[2]);
        mRemoteConnectionObject.unregisterCallback(callback);
    }

    public void testRemoteConnectionCallbacks_VideoState() {
        if (!shouldTestTelecom(mContext)) {
            return;
        }

        Handler handler = setupRemoteConnectionCallbacksTest();

        final InvokeCounter callbackInvoker =
                new InvokeCounter("testRemoteConnectionCallbacks_VideoState");
        RemoteConnection.Callback callback;

        callback = new RemoteConnection.Callback() {
            @Override
            public void onVideoStateChanged(RemoteConnection connection, int videoState) {
                callbackInvoker.invoke(connection, videoState);
            }
        };
        mRemoteConnectionObject.registerCallback(callback, handler);
        mRemoteConnection.setVideoState(VideoProfile.STATE_BIDIRECTIONAL);
        callbackInvoker.waitForCount(1, WAIT_FOR_STATE_CHANGE_TIMEOUT_MS);
        assertEquals(mRemoteConnectionObject, callbackInvoker.getArgs(0)[0]);
        assertEquals(VideoProfile.STATE_BIDIRECTIONAL, callbackInvoker.getArgs(0)[1]);
        mRemoteConnectionObject.unregisterCallback(callback);
    }

    public void testRemoteConnectionCallbacks_ConferenceableConnections() {
        if (!shouldTestTelecom(mContext)) {
            return;
        }

        Handler handler = setupRemoteConnectionCallbacksTest();

        final InvokeCounter callbackInvoker =
                new InvokeCounter("testRemoteConnectionCallbacks_ConferenceableConnections");
        RemoteConnection.Callback callback;

        callback = new RemoteConnection.Callback() {
            @Override
            public void onConferenceableConnectionsChanged(
                    RemoteConnection connection,
                    List<RemoteConnection> conferenceableConnections) {
                callbackInvoker.invoke(connection, conferenceableConnections);
            }
        };
        mRemoteConnectionObject.registerCallback(callback, handler);
        //Make the existing call active to add a new call
        final Call call = mInCallCallbacks.getService().getLastCall();
        mConnection.setActive();
        mRemoteConnection.setActive();
        assertCallState(call, Call.STATE_ACTIVE);
        placeAndVerifyCall();
        RemoteConnection newRemoteConnectionObject =
                verifyConnectionForOutgoingCall(1).getRemoteConnection();
        MockConnection newConnection = verifyConnectionForOutgoingCallOnRemoteCS(1);
        ArrayList<Connection> confList = new ArrayList<>();
        confList.add(newConnection);
        mRemoteConnection.setConferenceableConnections(confList);
        callbackInvoker.waitForCount(1, WAIT_FOR_STATE_CHANGE_TIMEOUT_MS);
        assertEquals(mRemoteConnectionObject, callbackInvoker.getArgs(0)[0]);
        //assertTrue(((List<RemoteConnection>)callbackInvoker.getArgs(0)[1]).contains(
                //newRemoteConnectionObject)); No "equals" method in RemoteConnection
        mRemoteConnectionObject.unregisterCallback(callback);
    }

    public void testRemoteConnectionCallbacks_VideoProvider() {
        if (!shouldTestTelecom(mContext)) {
            return;
        }

        Handler handler = setupRemoteConnectionCallbacksTest();

        final InvokeCounter callbackInvoker =
                new InvokeCounter("testRemoteConnectionCallbacks_VideoProvider");
        RemoteConnection.Callback callback;

        callback = new RemoteConnection.Callback() {
            @Override
            public void onVideoProviderChanged(
                    RemoteConnection connection, VideoProvider videoProvider) {
                callbackInvoker.invoke(connection, videoProvider);
            }
        };
        mRemoteConnectionObject.registerCallback(callback, handler);
        mRemoteConnection.createMockVideoProvider();
        MockVideoProvider mockVideoProvider = mRemoteConnection.getMockVideoProvider();
        mRemoteConnection.setVideoProvider(mockVideoProvider);
        callbackInvoker.waitForCount(1, WAIT_FOR_STATE_CHANGE_TIMEOUT_MS);
        assertEquals(mRemoteConnectionObject, callbackInvoker.getArgs(0)[0]);
        mRemoteConnectionObject.unregisterCallback(callback);
    }

    public void testRemoteConnectionCallbacks_Extras() {
        if (!shouldTestTelecom(mContext)) {
            return;
        }

        Handler handler = setupRemoteConnectionCallbacksTest();

        final InvokeCounter callbackInvoker =
                new InvokeCounter("testRemoteConnectionCallbacks_Extras");
        RemoteConnection.Callback callback;

        callback = new RemoteConnection.Callback() {
            @Override
            public void onExtrasChanged(RemoteConnection connection, Bundle extras) {
                callbackInvoker.invoke(connection, extras);
            }
        };
        mRemoteConnectionObject.registerCallback(callback, handler);
        Bundle extras = new Bundle();
        extras.putString(TelecomManager.EXTRA_CALL_DISCONNECT_MESSAGE, "Test");
        mRemoteConnection.setExtras(extras);
        callbackInvoker.waitForCount(1, WAIT_FOR_STATE_CHANGE_TIMEOUT_MS);
        assertEquals(mRemoteConnectionObject, callbackInvoker.getArgs(0)[0]);
        assertEquals(extras, callbackInvoker.getArgs(0)[1]);
        mRemoteConnectionObject.unregisterCallback(callback);

    }

    public void testRemoteConnectionCallbacks_Disconnect() {
        if (!shouldTestTelecom(mContext)) {
            return;
        }

        Handler handler = setupRemoteConnectionCallbacksTest();

        final InvokeCounter callbackInvoker =
                new InvokeCounter("testRemoteConnectionCallbacks_Disconnect");
        RemoteConnection.Callback callback;

        callback = new RemoteConnection.Callback() {
            @Override
            public void onDisconnected(
                    RemoteConnection connection,
                    DisconnectCause disconnectCause) {
                callbackInvoker.invoke(connection, disconnectCause);
            }
        };
        mRemoteConnectionObject.registerCallback(callback, handler);
        DisconnectCause cause = new DisconnectCause(DisconnectCause.LOCAL);
        mRemoteConnection.setDisconnected(cause);
        callbackInvoker.waitForCount(1, WAIT_FOR_STATE_CHANGE_TIMEOUT_MS);
        assertEquals(mRemoteConnectionObject, callbackInvoker.getArgs(0)[0]);
        assertEquals(cause, callbackInvoker.getArgs(0)[1]);
        mRemoteConnectionObject.unregisterCallback(callback);
    }

    public void testRemoteConnectionCallbacks_Destroy() {
        if (!shouldTestTelecom(mContext)) {
            return;
        }

        Handler handler = setupRemoteConnectionCallbacksTest();

        final InvokeCounter callbackInvoker =
                new InvokeCounter("testRemoteConnectionCallbacks_Destroy");
        RemoteConnection.Callback callback;

        callback = new RemoteConnection.Callback() {
            @Override
            public void onDestroyed(RemoteConnection connection) {
                callbackInvoker.invoke(connection);
            }
        };
        mRemoteConnectionObject.registerCallback(callback, handler);
        mRemoteConnection.destroy();
        callbackInvoker.waitForCount(1, WAIT_FOR_STATE_CHANGE_TIMEOUT_MS);
        assertEquals(mRemoteConnectionObject, callbackInvoker.getArgs(0)[0]);
        mRemoteConnectionObject.unregisterCallback(callback);
    }

    public void testRemoteConnectionVideoCallbacks_SessionModify() {
        if (!shouldTestTelecom(mContext)) {
            return;
        }

        setupRemoteConnectionVideoCallbacksTest();

        final InvokeCounter callbackInvoker =
                new InvokeCounter("testRemoteConnectionVideoCallbacks_SessionModify");
        final VideoProvider remoteVideoProvider = mRemoteConnectionObject.getVideoProvider();
        final MockVideoProvider mockVideoProvider = mRemoteConnection.getMockVideoProvider();
        RemoteConnection.VideoProvider.Callback videoCallback;

        videoCallback = new RemoteConnection.VideoProvider.Callback() {
            @Override
            public void onSessionModifyRequestReceived(
                    VideoProvider videoProvider,
                    VideoProfile videoProfile) {
                callbackInvoker.invoke(videoProvider, videoProfile);
            }

            @Override
            public void onSessionModifyResponseReceived(
                    VideoProvider videoProvider,
                    int status,
                    VideoProfile requestedProfile,
                    VideoProfile responseProfile) {
                callbackInvoker.invoke(videoProvider, status, requestedProfile, responseProfile);
            }
        };
        remoteVideoProvider.registerCallback(videoCallback);
        VideoProfile videoProfile = new VideoProfile(VideoProfile.STATE_BIDIRECTIONAL);
        mockVideoProvider.sendMockSessionModifyRequest(videoProfile);
        callbackInvoker.waitForCount(1, WAIT_FOR_STATE_CHANGE_TIMEOUT_MS);
        assertEquals(remoteVideoProvider, callbackInvoker.getArgs(0)[0]);
        assertEquals(videoProfile, callbackInvoker.getArgs(0)[1]);
        remoteVideoProvider.unregisterCallback(videoCallback);
    }

    public void testRemoteConnectionVideoCallbacks_SessionEvent() {
        if (!shouldTestTelecom(mContext)) {
            return;
        }

        setupRemoteConnectionVideoCallbacksTest();

        final InvokeCounter callbackInvoker =
                new InvokeCounter("testRemoteConnectionVideoCallbacks_SessionEvent");
        final VideoProvider remoteVideoProvider = mRemoteConnectionObject.getVideoProvider();
        final MockVideoProvider mockVideoProvider = mRemoteConnection.getMockVideoProvider();
        RemoteConnection.VideoProvider.Callback videoCallback;

        videoCallback = new RemoteConnection.VideoProvider.Callback() {
            @Override
            public void onCallSessionEvent(VideoProvider videoProvider, int event) {
                callbackInvoker.invoke(videoProvider, event);
            }
        };
        remoteVideoProvider.registerCallback(videoCallback);
        mockVideoProvider.handleCallSessionEvent(Connection.VideoProvider.SESSION_EVENT_RX_PAUSE);
        callbackInvoker.waitForCount(1, WAIT_FOR_STATE_CHANGE_TIMEOUT_MS);
        assertEquals(remoteVideoProvider, callbackInvoker.getArgs(0)[0]);
        assertEquals(Connection.VideoProvider.SESSION_EVENT_RX_PAUSE, callbackInvoker.getArgs(0)[1]);
        remoteVideoProvider.unregisterCallback(videoCallback);
    }

    public void testRemoteConnectionVideoCallbacks_PeerDimensions() {
        if (!shouldTestTelecom(mContext)) {
            return;
        }

        setupRemoteConnectionVideoCallbacksTest();

        final InvokeCounter callbackInvoker =
                new InvokeCounter("testRemoteConnectionVideoCallbacks_PeerDimensions");
        final VideoProvider remoteVideoProvider = mRemoteConnectionObject.getVideoProvider();
        final MockVideoProvider mockVideoProvider = mRemoteConnection.getMockVideoProvider();
        RemoteConnection.VideoProvider.Callback videoCallback;

        videoCallback = new RemoteConnection.VideoProvider.Callback() {
            @Override
            public void onPeerDimensionsChanged(VideoProvider videoProvider, int width,
                                                int height) {
                callbackInvoker.invoke(videoProvider, width, height);
            }
        };
        remoteVideoProvider.registerCallback(videoCallback);
        final int width = 100, heigth = 20;
        mockVideoProvider.changePeerDimensions(width, heigth);
        callbackInvoker.waitForCount(1, WAIT_FOR_STATE_CHANGE_TIMEOUT_MS);
        assertEquals(remoteVideoProvider, callbackInvoker.getArgs(0)[0]);
        assertEquals(width, callbackInvoker.getArgs(0)[1]);
        assertEquals(heigth, callbackInvoker.getArgs(0)[2]);
        remoteVideoProvider.unregisterCallback(videoCallback);
    }

    public void testRemoteConnectionVideoCallbacks_CallDataUsage() {
        if (!shouldTestTelecom(mContext)) {
            return;
        }

        setupRemoteConnectionVideoCallbacksTest();

        final InvokeCounter callbackInvoker =
                new InvokeCounter("testRemoteConnectionVideoCallbacks_CallDataUsage");
        final VideoProvider remoteVideoProvider = mRemoteConnectionObject.getVideoProvider();
        final MockVideoProvider mockVideoProvider = mRemoteConnection.getMockVideoProvider();
        RemoteConnection.VideoProvider.Callback videoCallback;

        videoCallback = new RemoteConnection.VideoProvider.Callback() {
            @Override
            public void onCallDataUsageChanged(VideoProvider videoProvider, long dataUsage) {
                callbackInvoker.invoke(videoProvider, dataUsage);
            }
        };
        remoteVideoProvider.registerCallback(videoCallback);
        long callDataUsage = 10000;
        mockVideoProvider.setCallDataUsage(callDataUsage);
        callbackInvoker.waitForCount(1, WAIT_FOR_STATE_CHANGE_TIMEOUT_MS);
        assertEquals(remoteVideoProvider, callbackInvoker.getArgs(0)[0]);
        assertEquals(callDataUsage, callbackInvoker.getArgs(0)[1]);
        remoteVideoProvider.unregisterCallback(videoCallback);
    }

    public void testRemoteConnectionVideoCallbacks_CameraCapabilities() {
        if (!shouldTestTelecom(mContext)) {
            return;
        }

        setupRemoteConnectionVideoCallbacksTest();

        final InvokeCounter callbackInvoker =
                new InvokeCounter("testRemoteConnectionVideoCallbacks_CameraCapabilities");
        final VideoProvider remoteVideoProvider = mRemoteConnectionObject.getVideoProvider();
        final MockVideoProvider mockVideoProvider = mRemoteConnection.getMockVideoProvider();
        RemoteConnection.VideoProvider.Callback videoCallback;

        videoCallback = new RemoteConnection.VideoProvider.Callback() {
            @Override
            public void onCameraCapabilitiesChanged(
                    VideoProvider videoProvider,
                    VideoProfile.CameraCapabilities cameraCapabilities) {
                callbackInvoker.invoke(videoProvider, cameraCapabilities);
            }
        };
        remoteVideoProvider.registerCallback(videoCallback);
        VideoProfile.CameraCapabilities capabilities = new VideoProfile.CameraCapabilities(100, 200);
        mockVideoProvider.changeCameraCapabilities(capabilities);
        callbackInvoker.waitForCount(1, WAIT_FOR_STATE_CHANGE_TIMEOUT_MS);
        assertEquals(remoteVideoProvider, callbackInvoker.getArgs(0)[0]);
        assertEquals(capabilities, callbackInvoker.getArgs(0)[1]);
        remoteVideoProvider.unregisterCallback(videoCallback);
    }

    public void testRemoteConnectionVideoCallbacks_VideoQuality() {
        if (!shouldTestTelecom(mContext)) {
            return;
        }

        setupRemoteConnectionVideoCallbacksTest();

        final InvokeCounter callbackInvoker =
                new InvokeCounter("testRemoteConnectionVideoCallbacks_VideoQuality");
        final VideoProvider remoteVideoProvider = mRemoteConnectionObject.getVideoProvider();
        final MockVideoProvider mockVideoProvider = mRemoteConnection.getMockVideoProvider();
        RemoteConnection.VideoProvider.Callback videoCallback;

        videoCallback = new RemoteConnection.VideoProvider.Callback() {
            @Override
            public void onVideoQualityChanged(VideoProvider videoProvider, int videoQuality) {
                callbackInvoker.invoke(videoProvider, videoQuality);
            }
        };
        remoteVideoProvider.registerCallback(videoCallback);
        final int videoQuality = 10;
        mockVideoProvider.changeVideoQuality(videoQuality);
        callbackInvoker.waitForCount(1, WAIT_FOR_STATE_CHANGE_TIMEOUT_MS);
        assertEquals(remoteVideoProvider, callbackInvoker.getArgs(0)[0]);
        assertEquals(videoQuality, callbackInvoker.getArgs(0)[1]);
        remoteVideoProvider.unregisterCallback(videoCallback);
    }


    private void verifyRemoteConnectionObject(RemoteConnection remoteConnection,
            Connection connection) {
        assertEquals(connection.getAddress(), remoteConnection.getAddress());
        assertEquals(connection.getAddressPresentation(),
                remoteConnection.getAddressPresentation());
        assertEquals(connection.getCallerDisplayName(), remoteConnection.getCallerDisplayName());
        assertEquals(connection.getCallerDisplayNamePresentation(),
                remoteConnection.getCallerDisplayNamePresentation());
        assertEquals(connection.getConnectionCapabilities(),
                remoteConnection.getConnectionCapabilities());
        assertEquals(connection.getDisconnectCause(), remoteConnection.getDisconnectCause());
        assertEquals(connection.getExtras(), remoteConnection.getExtras());
        assertEquals(connection.getStatusHints(), remoteConnection.getStatusHints());
        assertEquals(VideoProfile.STATE_AUDIO_ONLY, remoteConnection.getVideoState());
        assertNull(remoteConnection.getVideoProvider());
        assertTrue(remoteConnection.getConferenceableConnections().isEmpty());
    }

    private void addRemoteConnectionOutgoingCall() {
        try {
            MockConnectionService managerConnectionService = new MockConnectionService() {
                @Override
                public Connection onCreateOutgoingConnection(
                        PhoneAccountHandle connectionManagerPhoneAccount,
                        ConnectionRequest request) {
                    MockConnection connection = (MockConnection)super.onCreateOutgoingConnection(
                            connectionManagerPhoneAccount, request);
                    ConnectionRequest remoteRequest = new ConnectionRequest(
                            TEST_REMOTE_PHONE_ACCOUNT_HANDLE,
                            request.getAddress(),
                            request.getExtras());
                    RemoteConnection remoteConnection =
                            CtsConnectionService.createRemoteOutgoingConnectionToTelecom(
                                    TEST_REMOTE_PHONE_ACCOUNT_HANDLE, remoteRequest);
                    connection.setRemoteConnection(remoteConnection);
                    return connection;
                }
            };
            setupConnectionServices(managerConnectionService, null, FLAG_REGISTER | FLAG_ENABLE);
        } catch(Exception e) {
            fail("Error in setting up the connection services");
        }
        placeAndVerifyCall();
        /**
         * Retrieve the connection from both the connection services and see if the plumbing via
         * RemoteConnection object is working.
         */
        mConnection = verifyConnectionForOutgoingCall();
        mRemoteConnection = verifyConnectionForOutgoingCallOnRemoteCS();
        mRemoteConnectionObject = mConnection.getRemoteConnection();
    }

    private void addRemoteConnectionIncomingCall() {
        try {
            MockConnectionService managerConnectionService = new MockConnectionService() {
                @Override
                public Connection onCreateIncomingConnection(
                        PhoneAccountHandle connectionManagerPhoneAccount,
                        ConnectionRequest request) {
                    MockConnection connection = (MockConnection)super.onCreateIncomingConnection(
                            connectionManagerPhoneAccount, request);
                    ConnectionRequest remoteRequest = new ConnectionRequest(
                            TEST_REMOTE_PHONE_ACCOUNT_HANDLE,
                            request.getAddress(),
                            request.getExtras());
                    RemoteConnection remoteConnection =
                            CtsConnectionService.createRemoteIncomingConnectionToTelecom(
                                    TEST_REMOTE_PHONE_ACCOUNT_HANDLE, remoteRequest);
                    connection.setRemoteConnection(remoteConnection);
                    return connection;
                }
            };
            setupConnectionServices(managerConnectionService, null, FLAG_REGISTER | FLAG_ENABLE);
        } catch(Exception e) {
            fail("Error in setting up the connection services");
        }
        addAndVerifyNewIncomingCall(createTestNumber(), null);
        /**
         * Retrieve the connection from both the connection services and see if the plumbing via
         * RemoteConnection object is working.
         */
        mConnection = verifyConnectionForIncomingCall();
        mRemoteConnection = verifyConnectionForIncomingCallOnRemoteCS();
        mRemoteConnectionObject = mConnection.getRemoteConnection();
    }

    private Handler setupRemoteConnectionCallbacksTest() {
        addRemoteConnectionOutgoingCall();
        final Call call = mInCallCallbacks.getService().getLastCall();
        assertCallState(call, Call.STATE_DIALING);
        verifyRemoteConnectionObject(mRemoteConnectionObject, mRemoteConnection);

        // Create a looper thread for the callbacks.
        HandlerThread workerThread = new HandlerThread("CallbackThread");
        workerThread.start();
        Handler handler = new Handler(workerThread.getLooper());
        return handler;
    }

    private Handler setupRemoteConnectionVideoCallbacksTest() {
        addRemoteConnectionOutgoingCall();
        final Call call = mInCallCallbacks.getService().getLastCall();
        assertCallState(call, Call.STATE_DIALING);
        assertNotNull(mConnection);
        assertNotNull(mRemoteConnection);
        assertNotNull(mRemoteConnectionObject);
        verifyRemoteConnectionObject(mRemoteConnectionObject, mRemoteConnection);

        // Create a looper thread for the callbacks.
        HandlerThread workerThread = new HandlerThread("CallbackThread");
        workerThread.start();
        Handler handler = new Handler(workerThread.getLooper());

        final InvokeCounter callbackInvoker = new InvokeCounter("RemoteConnectionCallbacks");

        RemoteConnection.Callback callback = new RemoteConnection.Callback() {
            @Override
            public void onVideoProviderChanged(
                    RemoteConnection connection, VideoProvider videoProvider) {
                callbackInvoker.invoke(connection, videoProvider);
            }
        };
        mRemoteConnectionObject.registerCallback(callback, handler);
        mRemoteConnection.createMockVideoProvider();
        mRemoteConnection.setVideoProvider(mRemoteConnection.getMockVideoProvider());
        callbackInvoker.waitForCount(1, WAIT_FOR_STATE_CHANGE_TIMEOUT_MS);
        mRemoteConnectionObject.unregisterCallback(callback);
        return handler;
    }

}