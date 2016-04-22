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
package com.teradata.presto.yarn.test.fulfillment;

import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import com.teradata.presto.yarn.test.slider.Slider;
import com.teradata.tempto.Requirement;
import com.teradata.tempto.context.State;
import com.teradata.tempto.fulfillment.RequirementFulfiller;
import com.teradata.tempto.fulfillment.TestStatus;
import com.teradata.tempto.ssh.SshClient;
import org.apache.commons.io.filefilter.WildcardFileFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Named;

import java.io.File;
import java.io.FileFilter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;

@RequirementFulfiller.AutoSuiteLevelFulfiller(priority = 2)
public class SliderClusterFulfiller
        implements RequirementFulfiller
{
    private static final Logger log = LoggerFactory.getLogger(SliderClusterFulfiller.class);

    public static final String PACKAGE_NAME = "PRESTO";
    @Inject
    @Named("tests.app_package.path")
    private String prestoPackagePath;

    @Inject
    @Named("tests.slider.binary")
    private String sliderBinaryPath;
    private final Slider slider;

    @Inject
    public SliderClusterFulfiller(@Named("yarn") SshClient yarnSshClient)
    {
        this.slider = new Slider(yarnSshClient);
    }

    @Override
    public Set<State> fulfill(Set<Requirement> requirements)
    {
        log.info("fulfilling slider cluster");
        slider.install(Paths.get(sliderBinaryPath));

        Path presto_app_package = Paths.get(getPrestoAppPackagePath());
        log.info("Using Presto package from: " + presto_app_package);
        slider.installLocalPackage(presto_app_package, PACKAGE_NAME);

        return ImmutableSet.of(slider);
    }

    private String getPrestoAppPackagePath()
    {
        File dir = new File(prestoPackagePath);
        FileFilter fileFilter = new WildcardFileFilter("presto-yarn-package*.zip");
        File[] files = dir.listFiles(fileFilter);
        return files[0].getPath();
    }

    @Override
    public void cleanup(TestStatus testStatus)
    {
        slider.uninstallPackage(PACKAGE_NAME);
    }
}
