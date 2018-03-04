package net.jonathangiles.cloudsync.engine;

import com.google.inject.AbstractModule;
import net.jmob.guice.conf.core.ConfigurationModule;
import net.jonathangiles.cloudsync.engine.cloud.CloudStore;
import net.jonathangiles.cloudsync.engine.cloud.azure.AzureCloudStore;
import net.jonathangiles.cloudsync.engine.db.DataStore;
import net.jonathangiles.cloudsync.engine.db.jpa.JPADataStore;
import net.jonathangiles.cloudsync.engine.util.config.Config;

import java.io.File;

public class BackupEngineModule extends AbstractModule {

    @Override protected void configure() {
        // loading the config.json file into the Config class, which can then be injected into relevant places
        install(new ConfigurationModule().fromPath(new File("./")));
        requestInjection(Config.class);

        bind(CloudStore.class).to(AzureCloudStore.class);
//        bind(CloudStore.class).to(AwsCloudStore.class);
        bind(DataStore.class).to(JPADataStore.class);
    }
}