package ru.manager.ProgectManager.entitys;

import lombok.Getter;
import lombok.Setter;
import org.hibernate.Hibernate;

import javax.persistence.*;
import java.util.Objects;

@Entity
@Getter
@Setter
public class KanbanConnector {
    @Id
    @GeneratedValue
    private long id;

    private boolean canEdit;

    @OneToOne(optional = false)
    @JoinColumn(name = "kanban_id")
    private Kanban kanban;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) return false;
        KanbanConnector that = (KanbanConnector) o;
        return Objects.equals(id, that.id);
    }
}
