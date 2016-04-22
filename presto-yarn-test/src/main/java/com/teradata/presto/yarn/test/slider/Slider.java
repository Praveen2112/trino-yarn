/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.teradata.presto.yarn.test.slider;

import com.teradata.tempto.context.State;
import com.teradata.tempto.process.CommandExecutionException;
import com.teradata.tempto.ssh.SshClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.Optional;

import static com.google.common.base.Preconditions.checkState;
import static com.teradata.presto.yarn.test.utils.Resources.extractResource;

public class Slider
        implements State
{
    private static final Logger log = LoggerFactory.getLogger(Slider.class);

    private static final String SLIDER_REMOTE_CONF_DIR = "slider-0.80.0-incubating/conf/";
    private final SshClient sshClient;

    public Slider(SshClient sshClient)
    {
        this.sshClient = sshClient;
    }

    public void install(final Path sliderBinary)
    {
        if (isInstalled()) {
            log.info("Slider is already installed on cluster");
            return;
        }

        sshClient.command("unzip " + String.valueOf(upload(sliderBinary)));

        sshClient.upload(extractResource("/conf/slider/log4j.properties"), SLIDER_REMOTE_CONF_DIR);
        sshClient.upload(extractResource("/conf/slider/slider-client.xml"), SLIDER_REMOTE_CONF_DIR);
        sshClient.upload(extractResource("/conf/slider/slider-env.sh"), SLIDER_REMOTE_CONF_DIR);
    }

    private Path upload(Path path)
    {
        sshClient.upload(path, ".");
        return path.getFileName();
    }

    private boolean isInstalled()
    {
        try {
            action("help");
            return true;
        }
        catch (CommandExecutionException e) {
            log.debug("Checking if slider is installed", e);
            return false;
        }
    }

    public void installLocalPackage(Path clusterPackage, final String packageName)
    {
        upload(clusterPackage);
        Path remotePackage = clusterPackage.getFileName();
        action("package --install --name " + packageName + " --package " + remotePackage + " --replacepkg");
    }

    public void uninstallPackage(final String packageName)
    {
        action("package --delete --name " + packageName);
    }

    public void cleanup(final String appName)
    {
        try {
            stop(appName, true);
        }
        catch (CommandExecutionException e) {
            if (e.getExitStatus() == 69) {
                log.warn("Unable to stop cluster (it is not started)");
            }
            else {
                throw e;
            }
        }

        try {
            action("destroy " + appName);
        }
        catch (CommandExecutionException e) {
            log.warn("Unable to destroy cluster (is it not created?)", e);
        }
    }

    public void create(final String appName, final Path template, final Path resource)
    {
        action("create " + appName + " --template " + String.valueOf(upload(template)) + " --resources " + String.valueOf(upload(resource)));
        action("exists " + appName + " --live");
    }

    public Optional<SliderStatus> status(final String appName)
    {
        int count = 0;
        int maxRetries = 10;
        while (true) {
            try {
                action("status " + appName + " --out status_file");
                return Optional.of(new SliderStatus(sshClient.command("cat status_file")));
            }
            catch (CommandExecutionException e) {
                if (e.getExitStatus() == 70) {
                    log.warn("Unable to retrieve status, application is not yet running");
                    return Optional.empty();
                }
                else if (e.getExitStatus() == 56) {
                    log.warn("Unable to retrieve status,  node is unreachable temporarily. Retrying..");
                    if ((count = ++count) == maxRetries) {
                        throw e;
                    }
                }
                else {
                    throw e;
                }
            }
        }
    }

    public void stop(String clusterName, boolean force)
    {
        String forceArgument = force ? "--force" : "";
        action("stop " + clusterName + " " + forceArgument);
    }

    public void stop(String clusterName)
    {
        stop(clusterName, false);
    }

    public void flex(final String clusterName, final String component_name, final int component_count)
    {
        action("flex " + clusterName + " --component " + component_name + " " + String.valueOf(component_count));
    }

    public void action(final String arg)
    {
        sshClient.command("slider-0.80.0-incubating/bin/slider " + arg);
    }

    @Override
    public Optional<String> getName()
    {
        return Optional.empty();
    }
}
