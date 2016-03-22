package org.bndtools.templating.jgit.ui;

import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.bndtools.templating.jgit.Cache;
import org.bndtools.templating.jgit.GitHub;
import org.bndtools.templating.jgit.GithubRepoDetailsDTO;
import org.bndtools.utils.jface.ProgressRunner;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.dialogs.IMessageProvider;
import org.eclipse.jface.dialogs.ProgressMonitorDialog;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;

import aQute.bnd.header.Attrs;
import aQute.libg.tuple.Pair;

public class GitHubRepoDialog extends AbstractNewEntryDialog {

    private final BundleContext context = FrameworkUtil.getBundle(GitHubRepoDialog.class).getBundleContext();

    private final Cache cache = new Cache();
    private final String title;
    private final ExecutorService executor;

    private String repository = null;
    private String branch = null;
    private Text txtRepository;
    private Text txtBranch;

    public GitHubRepoDialog(Shell parentShell, String title) {
        super(parentShell);
        this.title = title;
        setShellStyle(getShellStyle() | SWT.RESIZE);
        this.executor = Executors.newCachedThreadPool();
    }

    @Override
    protected Control createDialogArea(Composite parent) {
        setTitle(title);
        Composite area = (Composite) super.createDialogArea(parent);
        Composite container = new Composite(area, SWT.NONE);
        container.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

        container.setLayout(new GridLayout(2, false));

        Label lblRepo = new Label(container, SWT.NONE);
        lblRepo.setText("Repository Name:");

        txtRepository = new Text(container, SWT.BORDER);
        txtRepository.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        if (repository != null)
            txtRepository.setText(repository);

        new Label(container, SWT.NONE).setText("Branch:");
        txtBranch = new Text(container, SWT.BORDER);
        txtBranch.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        txtBranch.setMessage("default branch");
        if (branch != null)
            txtBranch.setText(branch);

        final Button btnValidate = new Button(container, SWT.PUSH);
        btnValidate.setText("Validate");
        btnValidate.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 2, 1));
        ModifyListener modifyListener = new ModifyListener() {
            @Override
            public void modifyText(ModifyEvent ev) {
                repository = txtRepository.getText().trim();
                branch = txtBranch.getText().trim();
                updateButtons();
            }
        };
        txtRepository.addModifyListener(modifyListener);
        txtBranch.addModifyListener(modifyListener);

        btnValidate.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                setMessage(null, IMessageProvider.INFORMATION);
                try {
                    if (repository == null || repository.isEmpty())
                        throw new Exception("No repository name specified");

                    IRunnableWithProgress runnable = new IRunnableWithProgress() {
                        @Override
                        public void run(IProgressMonitor monitor) throws InvocationTargetException, InterruptedException {
                            try {
                                final GithubRepoDetailsDTO dto = new GitHub(cache, executor).loadRepoDetails(repository).getValue();
                                final URI cloneUri = URI.create(dto.clone_url);
                                btnValidate.getDisplay().asyncExec(new Runnable() {
                                    @Override
                                    public void run() {
                                        setMessage(String.format("Validated! Clone URL is '%s'. Default branch 'origin/%s'", cloneUri, dto.default_branch), IMessageProvider.INFORMATION);
                                    }
                                });
                            } catch (InvocationTargetException e) {
                                throw e;
                            } catch (Exception e) {
                                throw new InvocationTargetException(e);
                            }
                        }
                    };

                    ProgressRunner.execute(false, runnable, new ProgressMonitorDialog(getParentShell()), btnValidate.getDisplay());
                    setErrorMessage(null);
                } catch (InvocationTargetException ex) {
                    Throwable t = ex.getCause();
                    setErrorMessage(t.getClass().getSimpleName() + ": " + t.getMessage());
                } catch (Exception ex) {
                    setErrorMessage(ex.getClass().getSimpleName() + ": " + ex.getMessage());
                }
            }
        });

        return area;
    }

    @Override
    protected void createButtonsForButtonBar(Composite parent) {
        super.createButtonsForButtonBar(parent);

        Button ok = getButton(OK);
        ok.setText("Save");
        ok.setEnabled(repository != null);
    }

    private void updateButtons() {
        getButton(OK).setEnabled(repository != null && repository.trim().length() > 0);
    }

    @Override
    public void setEntry(Pair<String,Attrs> entry) {
        repository = entry.getFirst();
        Attrs attrs = entry.getSecond();
        branch = attrs.get("branch");
        if (txtRepository != null && !txtRepository.isDisposed())
            txtRepository.setText(repository);
        if (txtBranch != null && !txtBranch.isDisposed())
            txtBranch.setText(branch);
    }

    @Override
    public Pair<String,Attrs> getEntry() {
        Attrs attrs = new Attrs();
        if (branch != null && !branch.trim().isEmpty())
            attrs.put("branch", branch);
        return repository != null ? new Pair<String,Attrs>(repository.trim(), attrs) : null;
    }

    @Override
    public boolean close() {
        executor.shutdown();
        return super.close();
    }
}
