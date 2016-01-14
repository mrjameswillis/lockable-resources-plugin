package org.jenkins.plugins.lockableresources;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;

// McFoggy
// taken from commit: https://github.com/McFoggy/lockable-resources-plugin/commit/840871b1be7c6e0681ac4b34451fac4001ab8fe9
@ExportedBean
public class LockableResourceProperty extends AbstractDescribableImpl<LockableResourceProperty> {
    private String name;
    private String value;

    @DataBoundConstructor
    public LockableResourceProperty(String name, String value) {
        super();
        this.name = name;
        this.value = value == null || value.isEmpty() ? " " : value;
    }

    @Exported
    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }
    @Exported
    public String getValue() {
        return value;
    }
    public void setValue(String value) {
        this.value = value == null || value.isEmpty() ? " " : value;
    }
    @Override
    public String toString() {
        return name + "=" + (value == null || value.isEmpty() ? " " : value);
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<LockableResourceProperty> {
        @Override
        public String getDisplayName() {
            return "";
        }
    }
}
