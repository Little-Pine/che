/*******************************************************************************
 * Copyright (c) 2012-2016 Codenvy, S.A.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Codenvy, S.A. - initial API and implementation
 *******************************************************************************/

package org.eclipse.che.ide.command.action;

import com.google.common.base.Optional;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.assistedinject.Assisted;

import org.eclipse.che.api.core.model.machine.Machine;
import org.eclipse.che.api.promises.client.Operation;
import org.eclipse.che.api.promises.client.OperationException;
import org.eclipse.che.commons.annotation.Nullable;
import org.eclipse.che.ide.api.action.Action;
import org.eclipse.che.ide.api.action.ActionEvent;
import org.eclipse.che.ide.api.app.AppContext;
import org.eclipse.che.ide.api.command.CommandManager;
import org.eclipse.che.ide.api.command.CommandType;
import org.eclipse.che.ide.api.command.CommandTypeRegistry;
import org.eclipse.che.ide.api.command.ContextualCommand;
import org.eclipse.che.ide.api.icon.Icon;
import org.eclipse.che.ide.api.icon.IconRegistry;
import org.eclipse.che.ide.api.resources.Project;
import org.eclipse.che.ide.api.resources.Resource;
import org.eclipse.che.ide.api.selection.Selection;
import org.eclipse.che.ide.api.selection.SelectionAgent;
import org.eclipse.che.ide.command.palette.MachineSelectorPresenter;
import org.vectomatic.dom.svg.ui.SVGImage;
import org.vectomatic.dom.svg.ui.SVGResource;

import java.util.List;

/**
 * Action for executing a {@link ContextualCommand}.
 *
 * @author Artem Zatsarynnyi
 */
@Singleton
class ContextualCommandAction extends Action {

    private final ContextualCommand        command;
    private final CommandManager           commandManager;
    private final AppContext               appContext;
    private final IconRegistry             iconRegistry;
    private final CommandTypeRegistry      commandTypeRegistry;
    private final SelectionAgent           selectionAgent;
    private final MachineSelectorPresenter machineSelectorPresenter;

    @Inject
    ContextualCommandAction(@Assisted ContextualCommand command,
                            CommandManager commandManager,
                            AppContext appContext,
                            IconRegistry iconRegistry,
                            CommandTypeRegistry commandTypeRegistry,
                            SelectionAgent selectionAgent,
                            MachineSelectorPresenter machineSelectorPresenter) {
        super(command.getName());

        this.command = command;
        this.commandManager = commandManager;
        this.appContext = appContext;
        this.iconRegistry = iconRegistry;
        this.commandTypeRegistry = commandTypeRegistry;
        this.selectionAgent = selectionAgent;
        this.machineSelectorPresenter = machineSelectorPresenter;

        // set icon
        final SVGResource commandIcon = getCommandIcon();
        if (commandIcon != null) {
            getTemplatePresentation().setSVGResource(commandIcon);
        }
    }

    @Override
    public void update(ActionEvent e) {
        // it should be possible to execute any command
        // if machine is currently selected
        if (isMachineSelected()) {
            e.getPresentation().setEnabledAndVisible(true);

            return;
        }

        // let's check applicable projects
        final List<String> applicableProjects = command.getApplicableContext().getApplicableProjects();

        if (applicableProjects.isEmpty()) {
            e.getPresentation().setEnabledAndVisible(true);
        } else {
            // action should be visible only for the applicable projects

            final Resource currentResource = appContext.getResource();

            if (currentResource != null) {
                final Optional<Project> currentProjectOptional = currentResource.getRelatedProject();

                if (currentProjectOptional.isPresent()) {
                    final Project currentProject = currentProjectOptional.get();

                    if (applicableProjects.contains(currentProject.getPath())) {
                        e.getPresentation().setEnabledAndVisible(true);

                        return;
                    }
                }
            }

            e.getPresentation().setEnabledAndVisible(false);
        }
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (isMachineSelected()) {
            commandManager.executeCommand(command, getSelectedMachine());
        } else {
            machineSelectorPresenter.selectMachine().then(new Operation<Machine>() {
                @Override
                public void apply(Machine arg) throws OperationException {
                    commandManager.executeCommand(command, arg);
                }
            });
        }
    }

    /** Whether machine is currently selected? */
    private boolean isMachineSelected() {
        return getSelectedMachine() != null;
    }

    /** Returns the currently selected machine. */
    @Nullable
    private Machine getSelectedMachine() {
        final Selection<?> selection = selectionAgent.getSelection();

        if (selection != null && !selection.isEmpty() && selection.isSingleSelection()) {
            final Object possibleNode = selection.getHeadElement();

            if (possibleNode instanceof Machine) {
                return (Machine)possibleNode;
            }
        }

        return null;
    }

    private SVGResource getCommandIcon() {
        final String commandTypeId = command.getType();
        final CommandType commandType = commandTypeRegistry.getCommandTypeById(commandTypeId);

        if (commandType != null) {
            final Icon icon = iconRegistry.getIconIfExist(commandTypeId + ".commands.category.icon");

            if (icon != null) {
                final SVGImage svgImage = icon.getSVGImage();

                if (svgImage != null) {
                    return icon.getSVGResource();
                }
            }
        }

        return null;
    }
}
