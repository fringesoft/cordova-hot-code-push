package com.nordnetab.chcp.main.events;

import com.nordnetab.chcp.main.config.ApplicationConfig;

/**
 * Created by Nikolay Demyankov on 25.08.15.
 * <p/>
 * Event is send when update has been installed.
 */
public class UpdateCheckEvent extends WorkerEvent {

    public static final String EVENT_NAME = "chcp_updatechecked";

    /**
     * Class constructor.
     *
     * @param config application config that was used for installation
     */
    public UpdateCheckEvent(ApplicationConfig config,int result) {
        super(EVENT_NAME, null, config);
        this.data().put("result", result);
    }
}
