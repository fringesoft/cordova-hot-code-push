package com.nordnetab.chcp.main.updater;

import android.text.TextUtils;
import android.util.Log;

import com.nordnetab.chcp.main.config.ApplicationConfig;
import com.nordnetab.chcp.main.config.ContentConfig;
import com.nordnetab.chcp.main.config.ContentManifest;
import com.nordnetab.chcp.main.events.NothingToUpdateEvent;
import com.nordnetab.chcp.main.events.UpdateCheckEvent;
import com.nordnetab.chcp.main.events.UpdateDownloadErrorEvent;
import com.nordnetab.chcp.main.events.UpdateIsReadyToInstallEvent;
import com.nordnetab.chcp.main.events.WorkerEvent;
import com.nordnetab.chcp.main.model.ChcpError;
import com.nordnetab.chcp.main.model.ManifestDiff;
import com.nordnetab.chcp.main.model.ManifestFile;
import com.nordnetab.chcp.main.model.PluginFilesStructure;
import com.nordnetab.chcp.main.network.ApplicationConfigDownloader;
import com.nordnetab.chcp.main.network.ContentManifestDownloader;
import com.nordnetab.chcp.main.network.DownloadResult;
import com.nordnetab.chcp.main.network.FileDownloader;
import com.nordnetab.chcp.main.storage.ApplicationConfigStorage;
import com.nordnetab.chcp.main.storage.ContentManifestStorage;
import com.nordnetab.chcp.main.storage.IObjectFileStorage;
import com.nordnetab.chcp.main.utils.FilesUtility;
import com.nordnetab.chcp.main.utils.URLUtility;

import java.util.List;
import java.util.Map;

/**
 * Created by Nikolay Demyankov on 28.07.15.
 * <p/>
 * Worker, that implements update download logic.
 * During the download process events are dispatched to notify the subscribers about the progress.
 * <p/>
 * Used internally.
 */
class UpdateCheckWorker implements WorkerTask {

    private final String applicationConfigUrl;
    private final int appNativeVersion;
    private final PluginFilesStructure filesStructure;
    private final Map<String, String> requestHeaders;

    private IObjectFileStorage<ApplicationConfig> appConfigStorage;

    private ApplicationConfig oldAppConfig;

    private WorkerEvent resultEvent;

    /**
     * Constructor.
     *
     * @param request download request
     */
    UpdateCheckWorker(final UpdateDownloadRequest request) {
        applicationConfigUrl = request.getConfigURL();
        appNativeVersion = request.getCurrentNativeVersion();
        filesStructure = request.getCurrentReleaseFileStructure();
        requestHeaders = request.getRequestHeaders();
    }

    @Override
    public void run() {
        Log.d("CHCP", "Starting update check loader worker ");
        // initialize before running
        if (!init()) {
            return;
        }
        int result = 0;
        // download new application config
        final ApplicationConfig newAppConfig = downloadApplicationConfig(applicationConfigUrl);
        if (newAppConfig == null) {
            setErrorResult(ChcpError.FAILED_TO_DOWNLOAD_APPLICATION_CONFIG, null);
            return;
        }
        final ContentConfig newContentConfig = newAppConfig.getContentConfig();
        if (newContentConfig == null
                || TextUtils.isEmpty(newContentConfig.getReleaseVersion())
                || TextUtils.isEmpty(newContentConfig.getContentUrl())) {
            setErrorResult(ChcpError.NEW_APPLICATION_CONFIG_IS_INVALID, null);
            return;
        }

        // check if there is a new content version available
        if (newContentConfig.getReleaseVersion().equals(oldAppConfig.getContentConfig().getReleaseVersion())) {
            result = 0;
        }else{
            result = 1;
        }

        // check if current native version supports new content
        if (newContentConfig.getMinimumNativeVersion() > appNativeVersion) {
            result = 2;
        }

        // notify that we are done
        setSuccessResult(newAppConfig, result);

        Log.d("CHCP", "Loader worker has finished");
    }

    /**
     * Initialize all required variables before running the update.
     *
     * @return <code>true</code> if we are good to go, <code>false</code> - failed to initialize
     */
    private boolean init() {
        appConfigStorage = new ApplicationConfigStorage();

        // load current application config
        oldAppConfig = appConfigStorage.loadFromFolder(filesStructure.getWwwFolder());
        if (oldAppConfig == null) {
            setErrorResult(ChcpError.LOCAL_VERSION_OF_APPLICATION_CONFIG_NOT_FOUND, null);
            return false;
        }

        return true;
    }

    /**
     * Download application config from server.
     *
     * @param configUrl from what url it should be downloaded
     * @return new application config; <code>null</code> when failed to download
     */
    private ApplicationConfig downloadApplicationConfig(final String configUrl) {
        final ApplicationConfigDownloader downloader = new ApplicationConfigDownloader(configUrl, requestHeaders);
        final DownloadResult<ApplicationConfig> downloadResult = downloader.download();
        if (downloadResult.error != null) {
            Log.d("CHCP", "Failed to download application config");

            return null;
        }

        return downloadResult.value;
    }

    // region Events

    private void setErrorResult(final ChcpError error, final ApplicationConfig newAppConfig) {
        resultEvent = new UpdateDownloadErrorEvent(error, newAppConfig);
    }

    private void setSuccessResult(final ApplicationConfig newAppConfig, int result) {
        resultEvent = new UpdateCheckEvent(newAppConfig, result);
    }

    @Override
    public WorkerEvent result() {
        return resultEvent;
    }

    // endregion
}