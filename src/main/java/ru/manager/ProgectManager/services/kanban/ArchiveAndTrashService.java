package ru.manager.ProgectManager.services.kanban;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.manager.ProgectManager.entitys.User;
import ru.manager.ProgectManager.entitys.accessProject.CustomRoleWithKanbanConnector;
import ru.manager.ProgectManager.entitys.kanban.Kanban;
import ru.manager.ProgectManager.entitys.kanban.KanbanColumn;
import ru.manager.ProgectManager.entitys.kanban.KanbanElement;
import ru.manager.ProgectManager.entitys.kanban.Tag;
import ru.manager.ProgectManager.enums.ElementStatus;
import ru.manager.ProgectManager.enums.SearchElementType;
import ru.manager.ProgectManager.enums.TypeRoleProject;
import ru.manager.ProgectManager.exception.IncorrectStatusException;
import ru.manager.ProgectManager.repositories.*;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ArchiveAndTrashService {
    private final UserRepository userRepository;
    private final KanbanColumnRepository columnRepository;
    private final KanbanElementRepository elementRepository;
    private final TimeRemoverRepository timeRemoverRepository;
    private final KanbanRepository kanbanRepository;

    public void finalDeleteElementFromTrash(long id) {
        KanbanElement element = elementRepository.findById(id).get();
        if (element.getStatus() != ElementStatus.UTILISE)
            throw new IncorrectStatusException();

        KanbanColumn column = element.getKanbanColumn();
        column.getElements().remove(element);
        elementRepository.delete(element);
        columnRepository.save(column);
    }

    public boolean archive(long id, String userLogin) {
        User user = userRepository.findByUsername(userLogin);
        KanbanElement element = elementRepository.findById(id).get();
        Kanban kanban = element.getKanbanColumn().getKanban();
        if (canEditResource(kanban, user)) {
            if (element.getStatus() == ElementStatus.ARCHIVED)
                throw new IncorrectStatusException();

            timeRemoverRepository.findById(id).ifPresent(timeRemoverRepository::delete);

            element.setTimeOfUpdate(getEpochSeconds());
            element.setStatus(ElementStatus.ARCHIVED);
            KanbanColumn column = elementRepository.save(element).getKanbanColumn();
            column.getElements().stream()
                    .filter(e -> e.getSerialNumber() > element.getSerialNumber())
                    .forEach(e -> e.setSerialNumber(e.getSerialNumber() - 1));
            columnRepository.save(column);
            return true;
        } else {
            return false;
        }
    }

    public boolean reestablish(long id, String userLogin) {
        User user = userRepository.findByUsername(userLogin);
        KanbanElement element = elementRepository.findById(id).get();
        Kanban kanban = element.getKanbanColumn().getKanban();
        if (canEditResource(kanban, user)) {
            if (element.getStatus() == ElementStatus.ALIVE)
                throw new IncorrectStatusException();

            timeRemoverRepository.findById(id).ifPresent(timeRemoverRepository::delete);

            element.setTimeOfUpdate(getEpochSeconds());
            element.setStatus(ElementStatus.ALIVE);
            element.setSerialNumber(element.getKanbanColumn().getElements().stream()
                    .filter(e -> e.getStatus() == ElementStatus.ALIVE)
                    .mapToInt(KanbanElement::getSerialNumber)
                    .max().orElse(-1) + 1);
            elementRepository.save(element);
            return true;
        } else {
            return false;
        }
    }

    public Optional<List<KanbanElement>> findArchive(long kanbanId, String userLogin) {
        User user = userRepository.findByUsername(userLogin);
        Kanban kanban = kanbanRepository.findById(kanbanId).get();
        if (canSeeResource(kanban, user)) {
            return Optional.of(kanban.getKanbanColumns().stream()
                    .flatMap(c -> c.getElements().stream())
                    .filter(e -> e.getStatus() == ElementStatus.ARCHIVED)
                    .collect(Collectors.toList()));
        } else {
            return Optional.empty();
        }
    }

    public Optional<List<KanbanElement>> findTrash(long kanbanId, String userLogin) {
        User user = userRepository.findByUsername(userLogin);
        Kanban kanban = kanbanRepository.findById(kanbanId).get();
        if (canSeeResource(kanban, user)) {
            return Optional.of(kanban.getKanbanColumns().stream()
                    .flatMap(c -> c.getElements().stream())
                    .filter(e -> e.getStatus() == ElementStatus.UTILISE)
                    .collect(Collectors.toList()));
        } else {
            return Optional.empty();
        }
    }

    public Optional<Set<KanbanElement>> findElements(long kanbanId, SearchElementType type, String name,
                                                     ElementStatus from, String userLogin){
        if(from == ElementStatus.ALIVE)
            throw new IllegalArgumentException("Illegal element status for this method");
        Kanban kanban = kanbanRepository.findById(kanbanId).get();
        User user = userRepository.findByUsername(userLogin);
        if(canSeeResource(kanban, user)){
            if(type == SearchElementType.NAME){
                return Optional.of(findByName(kanban, name, from));
            } else if(type == SearchElementType.TAG){
                return Optional.of(findByTag(kanban, name, from));
            } else {
                Set<KanbanElement> set = new HashSet<>();
                set.addAll(findByName(kanban, name, from));
                set.addAll(findByTag(kanban, name, from));
                return Optional.of(set);
            }
        } else{
            return Optional.empty();
        }
    }


    private Set<KanbanElement> findByName(Kanban kanban, String name, ElementStatus elementStatus){
        return kanban.getKanbanColumns().stream()
                .flatMap(c -> c.getElements().stream())
                .filter(e -> e.getStatus() == elementStatus)
                .filter(e -> e.getName().toLowerCase().contains(name))
                .collect(Collectors.toSet());
    }

    private Set<KanbanElement> findByTag(Kanban kanban, String tagName, ElementStatus elementStatus){
        return kanban.getKanbanColumns().stream()
                .flatMap(c -> c.getElements().stream())
                .filter(e -> e.getStatus() == elementStatus)
                .filter(e -> e.getTags().stream().map(Tag::getText).map(String::toLowerCase)
                        .anyMatch(s -> s.contains(tagName)))
                .collect(Collectors.toSet());
    }

    private long getEpochSeconds() {
        return LocalDateTime.now().toEpochSecond(ZoneOffset.systemDefault().getRules().getOffset(Instant.now()));
    }

    private boolean canEditResource(Kanban kanban, User user){
        return kanban.getProject().getConnectors().stream().anyMatch(c -> c.getUser().equals(user)
                && (c.getRoleType() != TypeRoleProject.CUSTOM_ROLE
                || c.getCustomProjectRole().getCustomRoleWithKanbanConnectors().stream()
                .filter(CustomRoleWithKanbanConnector::isCanEdit)
                .anyMatch(kanbanConnector -> kanbanConnector.getKanban().equals(kanban))));
    }

    private boolean canSeeResource(Kanban kanban, User user){
        return kanban.getProject().getConnectors().stream().anyMatch(c -> c.getUser().equals(user)
                && (c.getRoleType() != TypeRoleProject.CUSTOM_ROLE
                || c.getCustomProjectRole().getCustomRoleWithKanbanConnectors().stream()
                .anyMatch(kanbanConnector -> kanbanConnector.getKanban().equals(kanban))));
    }
}
