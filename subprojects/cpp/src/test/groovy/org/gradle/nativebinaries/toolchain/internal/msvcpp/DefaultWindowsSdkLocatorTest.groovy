/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.nativebinaries.toolchain.internal.msvcpp

import net.rubygrapefruit.platform.WindowsRegistry
import org.gradle.internal.os.OperatingSystem
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.VersionNumber
import org.junit.Rule
import spock.lang.Specification

class DefaultWindowsSdkLocatorTest extends Specification {
    @Rule TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()
    final WindowsRegistry windowsRegistry = Stub(WindowsRegistry)
    final OperatingSystem operatingSystem = Stub(OperatingSystem) {
        isWindows() >> true
        getExecutableName(_ as String) >> { String exeName -> exeName }
    }
    final WindowsSdkLocator windowsSdkLocator = new DefaultWindowsSdkLocator(operatingSystem, windowsRegistry)

    def "uses highest version SDK found in registry"() {
        def dir1 = sdkDir("sdk1")
        def dir2 = sdkDir("sdk2")

        given:
        operatingSystem.findInPath(_) >> null
        windowsRegistry.getSubkeys(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, /SOFTWARE\Microsoft\Microsoft SDKs\Windows/) >> ["v1", "v2"]
        windowsRegistry.getStringValue(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, /SOFTWARE\Microsoft\Microsoft SDKs\Windows\v1/, "InstallationFolder") >> dir1.absolutePath
        windowsRegistry.getStringValue(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, /SOFTWARE\Microsoft\Microsoft SDKs\Windows\v1/, "ProductVersion") >> "7.0"
        windowsRegistry.getStringValue(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, /SOFTWARE\Microsoft\Microsoft SDKs\Windows\v1/, "ProductName") >> "sdk 1"
        windowsRegistry.getStringValue(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, /SOFTWARE\Microsoft\Microsoft SDKs\Windows\v2/, "InstallationFolder") >> dir2.absolutePath
        windowsRegistry.getStringValue(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, /SOFTWARE\Microsoft\Microsoft SDKs\Windows\v2/, "ProductVersion") >> "7.1"
        windowsRegistry.getStringValue(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, /SOFTWARE\Microsoft\Microsoft SDKs\Windows\v2/, "ProductName") >> "sdk 2"

        when:
        def result = windowsSdkLocator.locateWindowsSdks(null)

        then:
        result.available
        result.sdk.name == "sdk 2"
        result.sdk.version == VersionNumber.parse("7.1")
        result.sdk.baseDir == dir2
    }

    def "uses windows kit if version is higher than windows SDK"() {
        def dir1 = sdkDir("sdk1")
        def dir2 = kitDir("sdk2")

        given:
        operatingSystem.findInPath(_) >> null
        windowsRegistry.getSubkeys(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, /SOFTWARE\Microsoft\SDKs\Windows/) >> ["v1"]
        windowsRegistry.getStringValue(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, /SOFTWARE\Microsoft\Microsoft SDKs\Windows\v1/, "InstallationFolder") >> dir1.absolutePath
        windowsRegistry.getStringValue(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, /SOFTWARE\Microsoft\Microsoft SDKs\Windows\v1/, "ProductVersion") >> "7.1"
        windowsRegistry.getStringValue(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, /SOFTWARE\Microsoft\Microsoft SDKs\Windows\v1/, "ProductName") >> "sdk 1"
        windowsRegistry.getStringValue(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, /SOFTWARE\Microsoft\Windows Kits\Installed Roots/, "KitsRoot81") >> dir2.absolutePath

        when:
        def result = windowsSdkLocator.locateWindowsSdks(null)

        then:
        result.available
        result.sdk.name == "Windows Kit 8.1"
        result.sdk.version == VersionNumber.parse("8.1")
        result.sdk.baseDir == dir2
    }

    def "locates windows SDK based on executables in path"() {
        def sdkDir = sdkDir("sdk")

        given:
        operatingSystem.findInPath("rc.exe") >> sdkDir.file("bin/rc.exe")

        when:
        def result = windowsSdkLocator.locateWindowsSdks(null)

        then:
        result.available
        result.sdk.name == "Path-resolved Windows SDK"
        result.sdk.version == VersionNumber.UNKNOWN
        result.sdk.baseDir == sdkDir
    }

    def "uses windows SDK using specified install dir"() {
        def sdkDir = sdkDir("sdk")

        given:
        operatingSystem.findInPath(_) >> null

        when:
        def result = windowsSdkLocator.locateWindowsSdks(sdkDir)

        then:
        result.available
        result.sdk.name == "User-provided Windows SDK"
        result.sdk.version == VersionNumber.UNKNOWN
        result.sdk.baseDir == sdkDir
    }

    def "fills in meta-data from registry for SDK discovered using the path"() {
        def sdkDir = sdkDir("sdk1")

        given:
        operatingSystem.findInPath("rc.exe") >> sdkDir.file("bin/rc.exe")

        and:
        windowsRegistry.getSubkeys(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, /SOFTWARE\Microsoft\Microsoft SDKs\Windows/) >> ["v1"]
        windowsRegistry.getStringValue(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, /SOFTWARE\Microsoft\Microsoft SDKs\Windows\v1/, "InstallationFolder") >> sdkDir.absolutePath
        windowsRegistry.getStringValue(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, /SOFTWARE\Microsoft\Microsoft SDKs\Windows\v1/, "ProductVersion") >> "7.0"
        windowsRegistry.getStringValue(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, /SOFTWARE\Microsoft\Microsoft SDKs\Windows\v1/, "ProductName") >> "installed sdk"

        when:
        def result = windowsSdkLocator.locateWindowsSdks(null)

        then:
        result.available
        result.sdk.name == "installed sdk"
        result.sdk.version == VersionNumber.parse("7.0")
        result.sdk.baseDir == sdkDir
    }

    def sdkDir(String name) {
        def dir = tmpDir.createDir(name)
        dir.createFile("bin/rc.exe")
        dir.createFile("lib/kernel32.lib")
        return dir
    }

    def kitDir(String name) {
        def dir = tmpDir.createDir(name)
        dir.createFile("bin/x86/rc.exe")
        dir.createFile("lib/win8/um/x86/kernel32.lib")
        return dir
    }
}
