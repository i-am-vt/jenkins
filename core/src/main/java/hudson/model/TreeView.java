/*
 * The MIT License
 * 
 * Copyright (c) 2004-2010, Sun Microsystems, Inc., Kohsuke Kawaguchi
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package hudson.model;

import hudson.model.Descriptor.FormException;
import hudson.util.CaseInsensitiveComparator;
import hudson.Indenter;
import hudson.Extension;
import jenkins.util.SystemProperties;
import hudson.views.ViewsTabBar;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CopyOnWriteArrayList;
import org.kohsuke.stapler.interceptor.RequirePOST;
import org.kohsuke.stapler.verb.POST;

/**
 *
 * <h2>EXPERIMENTAL</h2>
 * <p>
 * The intention is to eventually merge this with the {@link ListView}.
 * This class is here for experimentation.
 *
 * <p>
 * Until this class is sufficiently stable, there's no need for l10n.
 * TODO: use ViewGroupMixIn
 *
 * @author Kohsuke Kawaguchi
 */
public class TreeView extends View implements ViewGroup {
    /**
     * List of job names. This is what gets serialized.
     */
    private final Set<String> jobNames
        = new TreeSet<>(CaseInsensitiveComparator.INSTANCE);

    /**
     * Nested views.
     */
    private final CopyOnWriteArrayList<View> views = new CopyOnWriteArrayList<>();

    @DataBoundConstructor
    public TreeView(String name) {
        super(name);
    }

    /**
     * Returns {@link Indenter} that has the fixed indentation width.
     * Used for assisting view rendering.
     */
    public Indenter createFixedIndenter(String d) {
        final int depth = Integer.parseInt(d);
        return new Indenter() {
            @Override
            protected int getNestLevel(Job job) { return depth; }
        };
    }

    /**
     * Returns a read-only view of all {@link Job}s in this view.
     *
     * <p>
     * This method returns a separate copy each time to avoid
     * concurrent modification issue.
     */
    @Override
    public synchronized List<TopLevelItem> getItems() {
        return Jenkins.get().getItems();
    }

    @Override
    public boolean contains(TopLevelItem item) {
        return true;
    }

    @Override
    @POST
    public TopLevelItem doCreateItem(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        ItemGroup<? extends TopLevelItem> ig = getOwner().getItemGroup();
        if (ig instanceof ModifiableItemGroup) {
            TopLevelItem item = ((ModifiableItemGroup<? extends TopLevelItem>)ig).doCreateItem(req, rsp);
            if(item!=null) {
                jobNames.add(item.getName());
                owner.save();
            }
            return item;
        }
        return null;
    }

    // TODO listen for changes that might affect jobNames

    @Override
    protected void submit(StaplerRequest req) throws IOException, ServletException, FormException {
    }

    @Override
    public boolean canDelete(View view) {
        return true;
    }

    @Override
    public void deleteView(View view) throws IOException {
        views.remove(view);
    }

    @Override
    public Collection<View> getViews() {
        return Collections.unmodifiableList(views);
    }

    @Override
    public View getView(String name) {
        //Top level views returned first if match
        for (View v : views) {
            if (v.getViewName().equals(name)) {
                return v;
            }
        }
        for (View v : views) {
            if (v instanceof ViewGroup) {
                View nestedView = ((ViewGroup) v).getView(name);
                if (nestedView != null) {
                    return nestedView;
                }
            }
        }
        return null;
    }

    @Override
    public void onViewRenamed(View view, String oldName, String newName) {
        // noop
    }

    @RequirePOST
    public void doCreateView( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException, FormException {
        checkPermission(View.CREATE);
        views.add(View.create(req,rsp,this));
        save();
    }

    // this feature is not public yet
    @Extension
    public static ViewDescriptor register() {
        if(SystemProperties.getBoolean("hudson.TreeView"))
            return new DescriptorImpl();
        else
            return null;
    }

    public static final class DescriptorImpl extends ViewDescriptor {
        @Override
        public String getDisplayName() {
            return "Tree View";
        }
    }

    @Override
    public ViewsTabBar getViewsTabBar() {
        return Jenkins.get().getViewsTabBar();
    }

    @Override
    public ItemGroup<? extends TopLevelItem> getItemGroup() {
        return getOwner().getItemGroup();
    }

    @Override
    public List<Action> getViewActions() {
        return owner.getViewActions();
    }

}
