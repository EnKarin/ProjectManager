package ru.manager.ProgectManager.services.documents;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.manager.ProgectManager.DTO.request.documents.CreatePageRequest;
import ru.manager.ProgectManager.DTO.request.documents.TransportPageRequest;
import ru.manager.ProgectManager.DTO.response.documents.PageNameAndUpdateDateResponse;
import ru.manager.ProgectManager.DTO.response.documents.PageResponse;
import ru.manager.ProgectManager.entitys.Project;
import ru.manager.ProgectManager.entitys.accessProject.CustomRoleWithDocumentConnector;
import ru.manager.ProgectManager.entitys.accessProject.UserWithProjectConnector;
import ru.manager.ProgectManager.entitys.documents.Page;
import ru.manager.ProgectManager.entitys.user.User;
import ru.manager.ProgectManager.entitys.user.VisitMark;
import ru.manager.ProgectManager.enums.ResourceType;
import ru.manager.ProgectManager.enums.TypeRoleProject;
import ru.manager.ProgectManager.repositories.*;
import ru.manager.ProgectManager.services.user.VisitMarkUpdater;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PageService {
    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;
    private final PageRepository pageRepository;
    private final CustomRoleWithDocumentConnectorRepository documentConnectorRepository;
    private final CustomProjectRoleRepository roleRepository;
    private final VisitMarkUpdater visitMarkUpdater;

    public Optional<Long> createPage(CreatePageRequest request, String userLogin) {
        User user = userRepository.findByUsername(userLogin);
        Project project = projectRepository.findById(request.getProjectId()).orElseThrow();
        if (canEditResource(project, user)) {
            Page page = new Page();
            page.setContent(request.getContent());
            page.setName(request.getName().trim());
            page.setProject(project);
            page.setUpdateTime(getEpochSeconds());
            page.setPublished(false);
            page.setOwner(user);
            Optional<Page> parent = pageRepository.findById(request.getParentId());
            if (parent.isPresent()) {
                page.setParent(parent.get());
                page.setRoot(parent.get().getRoot() == null ? parent.get() : parent.get().getRoot());
                page.setSerialNumber((short) parent.get().getSubpages().size());
            } else {
                page.setRoot(null);
                page.setParent(null);
                page.setSerialNumber((short) project.getPages().stream().filter(p -> p.getRoot() == null).count());
            }
            page = pageRepository.save(page);

            project.getPages().add(page);
            projectRepository.save(project);
            return Optional.of(page.getId());
        } else {
            return Optional.empty();
        }
    }

    public boolean deletePage(long id, String userLogin) {
        User user = userRepository.findByUsername(userLogin);
        Page page = pageRepository.findById(id).orElseThrow();
        Project project = page.getProject();
        if (canEditResource(project, user) && canEditPage(page, user)
                && (page.getOwner().equals(user)) || isPublishPipeline(page)) {
            if (page.getParent() != null) {
                Page parent = page.getParent();
                parent.getSubpages().remove(page);
                parent.getSubpages().parallelStream()
                        .filter(p -> p.getSerialNumber() > page.getSerialNumber())
                        .forEach(p -> p.setSerialNumber((short) (p.getSerialNumber() - 1)));
                pageRepository.save(parent);
            } else {
                project.getAvailableRole().forEach(role -> role.getCustomRoleWithDocumentConnectors().parallelStream()
                        .filter(connector -> connector.getPage().equals(page))
                        .forEach(connector -> {
                            role.getCustomRoleWithDocumentConnectors().remove(connector);
                            documentConnectorRepository.delete(connector);
                            roleRepository.save(role);
                        }));
                project.getPages().parallelStream()
                        .filter(p -> p.getRoot() == null)
                        .filter(p -> p.getSerialNumber() > page.getSerialNumber())
                        .forEach(p -> p.setSerialNumber((short) (p.getSerialNumber() - 1)));
            }

            visitMarkUpdater.deleteVisitMark(project, page.getId(), ResourceType.KANBAN);

            project.getPages().remove(page);
            projectRepository.save(project);
            pageRepository.delete(page);
            return true;
        } else {
            return false;
        }
    }

    public boolean rename(long id, String name, String userLogin) {
        User user = userRepository.findByUsername(userLogin);
        Page page = pageRepository.findById(id).orElseThrow();
        if (canEditPage(page, user) && (page.getOwner().equals(user) || isPublishPipeline(page))) {
            page.setName(name.trim());
            page.setUpdateTime(getEpochSeconds());
            pageRepository.save(page);
            visitMarkUpdater.redactVisitMark(page);
            return true;
        } else {
            return false;
        }
    }

    public boolean setContent(long id, String content, String userLogin) {
        User user = userRepository.findByUsername(userLogin);
        Page page = pageRepository.findById(id).orElseThrow();
        if (canEditPage(page, user) && (page.getOwner().equals(user)) || isPublishPipeline(page)) {
            page.setContent(content);
            page.setUpdateTime(getEpochSeconds());
            pageRepository.save(page);
            return true;
        } else {
            return false;
        }
    }

    public boolean publish(long id, String userLogin) {
        User user = userRepository.findByUsername(userLogin);
        Page page = pageRepository.findById(id).orElseThrow();
        if (page.getOwner().equals(user)) {
            page.setPublished(true);
            pageRepository.save(page);
            return true;
        } else {
            return false;
        }
    }

    public Optional<Page> find(long id, String userLogin) {
        User user = userRepository.findByUsername(userLogin);
        Page page = pageRepository.findById(id).orElseThrow();
        if (canSeePage(page, user) && (page.getOwner().equals(user) || isPublishPipeline(page))) {
            visitMarkUpdater.updateVisitMarks(user, page);
            return Optional.of(page);
        } else {
            return Optional.empty();
        }
    }

    public Optional<List<PageResponse>> findAllRoot(long projectId, String userLogin) {
        User user = userRepository.findByUsername(userLogin);
        Project project = projectRepository.findById(projectId).orElseThrow();
        Optional<UserWithProjectConnector> withProjectConnector = project.getConnectors().stream()
                .filter(c -> c.getUser().equals(user)).findAny();
        if (withProjectConnector.isPresent()) {
            if (withProjectConnector.get().getRoleType() == TypeRoleProject.CUSTOM_ROLE) {
                return Optional.of(withProjectConnector.get().getCustomProjectRole().getCustomRoleWithDocumentConnectors()
                        .stream()
                        .map(CustomRoleWithDocumentConnector::getPage)
                        .filter(page -> page.getOwner().equals(user) || isPublishPipeline(page))
                        .sorted(Comparator.comparing(Page::getSerialNumber))
                        .map(PageResponse::new)
                        .collect(Collectors.toList()));
            } else {
                return Optional.of(project.getPages().stream()
                        .filter(p -> p.getRoot() == null)
                        .filter(page -> page.getOwner().equals(user) || isPublishPipeline(page))
                        .sorted(Comparator.comparing(Page::getSerialNumber))
                        .map(PageResponse::new)
                        .collect(Collectors.toList()));
            }
        } else {
            return Optional.empty();
        }
    }

    public Optional<List<PageNameAndUpdateDateResponse>> findByName(long projectId, String inputName, String userLogin, int zoneId) {
        String name = inputName.trim().toLowerCase();
        User user = userRepository.findByUsername(userLogin);
        Project project = projectRepository.findById(projectId).orElseThrow();
        Optional<UserWithProjectConnector> withProjectConnector = project.getConnectors().stream()
                .filter(c -> c.getUser().equals(user)).findAny();
        if (withProjectConnector.isPresent()) {
            if (withProjectConnector.get().getRoleType() == TypeRoleProject.CUSTOM_ROLE) {
                return Optional.of(project.getPages().parallelStream()
                        .filter(page -> withProjectConnector.get().getCustomProjectRole()
                                .getCustomRoleWithDocumentConnectors().stream()
                                .map(CustomRoleWithDocumentConnector::getPage)
                                .anyMatch(root -> root.equals(page) || root.equals(page.getRoot())))
                        .filter(page -> page.getOwner().equals(user) || isPublishPipeline(page))
                        .filter(p -> p.getName().toLowerCase().contains(name))
                        .sorted(Comparator.comparing(Page::getUpdateTime).reversed())
                        .map(p -> new PageNameAndUpdateDateResponse(p, zoneId))
                        .collect(Collectors.toList()));
            } else {
                return Optional.of(project.getPages().parallelStream()
                        .filter(page -> page.getOwner().equals(user) || isPublishPipeline(page))
                        .filter(p -> p.getName().toLowerCase().contains(name))
                        .sorted(Comparator.comparing(Page::getUpdateTime).reversed())
                        .map(p -> new PageNameAndUpdateDateResponse(p, zoneId))
                        .collect(Collectors.toList()));
            }
        } else {
            return Optional.empty();
        }
    }

    public Optional<List<PageNameAndUpdateDateResponse>> findAllWithSort(long projectId, String userLogin, int timeZoneId) {
        User user = userRepository.findByUsername(userLogin);
        Project project = projectRepository.findById(projectId).orElseThrow();
        Optional<UserWithProjectConnector> withProjectConnector = project.getConnectors().stream()
                .filter(c -> c.getUser().equals(user)).findAny();
        if (withProjectConnector.isPresent()) {
            if (withProjectConnector.get().getRoleType() == TypeRoleProject.CUSTOM_ROLE) {
                return Optional.of(project.getPages().stream()
                        .filter(page -> withProjectConnector.get().getCustomProjectRole()
                                .getCustomRoleWithDocumentConnectors().stream()
                                .map(CustomRoleWithDocumentConnector::getPage)
                                .anyMatch(root -> root.equals(page) || root.equals(page.getRoot())))
                        .filter(page -> page.getOwner().equals(user) || isPublishPipeline(page))
                        .sorted(Comparator.comparing(Page::getUpdateTime).reversed())
                        .map(page -> new PageNameAndUpdateDateResponse(page, timeZoneId))
                        .collect(Collectors.toList()));
            } else {
                return Optional.of(project.getPages().stream()
                        .filter(page -> page.getOwner().equals(user) || isPublishPipeline(page))
                        .sorted(Comparator.comparing(Page::getUpdateTime).reversed())
                        .map(page -> new PageNameAndUpdateDateResponse(page, timeZoneId))
                        .collect(Collectors.toList()));
            }
        } else {
            return Optional.empty();
        }
    }

    public Optional<List<PageNameAndUpdateDateResponse>> findLastSeeDocument(long projectId, String userLogin,
                                                                             int zoneId) {
        Optional<Project> project = projectRepository.findById(projectId);
        return project.map(proj -> userRepository.findByUsername(userLogin).getVisitMarks().stream()
                .filter(visitMark -> visitMark.getResourceType() == ResourceType.DOCUMENT)
                .sorted(Comparator.comparing(VisitMark::getSerialNumber))
                .map(visitMark -> pageRepository.findById(visitMark.getResourceId()))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .filter(page -> page.getProject().equals(proj))
                .map(page -> new PageNameAndUpdateDateResponse(page, zoneId))
                .collect(Collectors.toList()));
    }

    public Optional<List<PageResponse>> findSubpages(long id, String userLogin) {
        Page page = pageRepository.findById(id).orElseThrow();
        User user = userRepository.findByUsername(userLogin);
        if (canSeePage(page, user)) {
            return Optional.of(page.getSubpages().stream()
                    .filter(p -> p.isPublished() || user.equals(p.getOwner()))
                    .sorted(Comparator.comparing(Page::getSerialNumber))
                    .map(PageResponse::new)
                    .collect(Collectors.toList()));
        } else {
            return Optional.empty();
        }
    }

    public boolean transport(TransportPageRequest request, String userLogin) {
        User user = userRepository.findByUsername(userLogin);
        Page transportedPage = pageRepository.findById(request.getId()).orElseThrow();
        if (canEditPage(transportedPage, user) &&
                (transportedPage.getOwner().equals(user) || isPublishPipeline(transportedPage))) {
            Optional<Page> newParent = pageRepository.findById(request.getNewParentId());
            short fromIndex = transportedPage.getSerialNumber();
            Page oldParent = transportedPage.getParent();
            newParent.ifPresentOrElse(parent -> {
                // перемещение внтри родительского раздела
                if (parent.equals(oldParent)) {
                    if (fromIndex < request.getIndex()) {
                        parent.getSubpages().parallelStream()
                                .filter(page -> page.getSerialNumber() > fromIndex)
                                .filter(page -> page.getSerialNumber() <= request.getIndex())
                                .forEach(page -> {
                                    page.setSerialNumber((short) (page.getSerialNumber() - 1));
                                    pageRepository.save(page);
                                });
                    } else {
                        parent.getSubpages().parallelStream()
                                .filter(page -> page.getSerialNumber() < fromIndex)
                                .filter(page -> page.getSerialNumber() >= request.getIndex())
                                .forEach(page -> {
                                    page.setSerialNumber((short) (page.getSerialNumber() + 1));
                                    pageRepository.save(page);
                                });
                    }
                } else {
                    oldParent.getSubpages().parallelStream()
                            .filter(page -> page.getSerialNumber() > fromIndex)
                            .forEach(page -> {
                                page.setSerialNumber((short) (page.getSerialNumber() - 1));
                                pageRepository.save(page);
                            });
                    parent.getSubpages().parallelStream()
                            .filter(page -> page.getSerialNumber() >= request.getIndex())
                            .forEach(page -> {
                                page.setSerialNumber((short) (page.getSerialNumber() + 1));
                                pageRepository.save(page);
                            });
                    transportedPage.setParent(parent);
                    recursiveSetRoot(transportedPage, parent.getRoot() == null ? parent : parent.getRoot());

                    oldParent.getSubpages().remove(transportedPage);
                    parent.getSubpages().add(transportedPage);
                    pageRepository.save(oldParent);
                    pageRepository.save(parent);
                }
            }, () -> { // перемещение в корень
                oldParent.getSubpages().parallelStream()
                        .filter(page -> page.getSerialNumber() > fromIndex)
                        .forEach(page -> {
                            page.setSerialNumber((short) (page.getSerialNumber() - 1));
                            pageRepository.save(page);
                        });
                oldParent.getSubpages().remove(transportedPage);
                pageRepository.save(oldParent);
                transportedPage.getSubpages().forEach(subpage -> recursiveSetRoot(subpage, transportedPage));

                transportedPage.setRoot(null);
                transportedPage.setParent(null);
                transportedPage.getProject().getPages().parallelStream()
                        .filter(page -> page.getRoot() == null)
                        .filter(page -> page.getSerialNumber() >= request.getIndex())
                        .forEach(page -> {
                            page.setSerialNumber((short) (page.getSerialNumber() + 1));
                            pageRepository.save(page);
                        });
            });
            transportedPage.setSerialNumber(request.getIndex());
            pageRepository.save(transportedPage);
            return true;
        } else {
            return false;
        }
    }

    private boolean isPublishPipeline(Page page) {
        while (page != null) {
            if (!page.isPublished())
                return false;
            page = page.getParent();
        }
        return true;
    }

    // установка нового корня в случае перенесения подстраницы из одного корневого каталга в другой
    private void recursiveSetRoot(Page now, Page root) {
        now.setRoot(root);
        now.getSubpages().forEach(subpage -> recursiveSetRoot(subpage, root));
    }

    private long getEpochSeconds() {
        return LocalDateTime.now().toEpochSecond(ZoneOffset.systemDefault().getRules().getOffset(Instant.now()));
    }

    private boolean canEditPage(Page page, User user) {
        Page root = (page.getRoot() == null ? page : page.getRoot());
        return page.getProject().getConnectors().stream().anyMatch(c -> c.getUser().equals(user)
                && (c.getRoleType() != TypeRoleProject.CUSTOM_ROLE
                || c.getCustomProjectRole().getCustomRoleWithDocumentConnectors().stream()
                .filter(CustomRoleWithDocumentConnector::isCanEdit)
                .anyMatch(connector -> connector.getPage().equals(root))));
    }

    public boolean canEditPage(Page page, String userLogin) {
        User user = userRepository.findByUsername(userLogin);
        return canEditPage(page, user);
    }

    private boolean canSeePage(Page page, User user) {
        Page root = (page.getRoot() == null ? page : page.getRoot());
        return page.getProject().getConnectors().stream().anyMatch(c -> c.getUser().equals(user)
                && (c.getRoleType() != TypeRoleProject.CUSTOM_ROLE
                || c.getCustomProjectRole().getCustomRoleWithDocumentConnectors().stream()
                .anyMatch(connector -> connector.getPage().equals(root))));
    }

    private boolean canEditResource(Project project, User user) {
        return project.getConnectors().stream().anyMatch(c -> c.getUser().equals(user)
                && (c.getRoleType() == TypeRoleProject.ADMIN || (c.getRoleType() == TypeRoleProject.CUSTOM_ROLE
                && c.getCustomProjectRole().isCanEditResources())));
    }
}
