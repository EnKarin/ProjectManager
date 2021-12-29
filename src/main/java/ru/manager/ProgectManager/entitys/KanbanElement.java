package ru.manager.ProgectManager.entitys;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;

@Entity
@Table(name = "kanban_element")
@Getter
@Setter
public class KanbanElement {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private long id;

    @ManyToOne
    @JoinColumn(name = "owner_id")
    private User owner;

    @ManyToOne
    @JoinColumn(name = "last_redactor_id")
    private User lastRedactor;

    @Column(nullable = false)
    private int serialNumber;

    @Column(nullable = false)
    private String name;

    @Column
    private String tag;

    @Column
    private String content;

    @Column(length = 4_194_304)
    @Lob
    private byte[] photo;

    @Column
    private String extension;

    @ManyToOne
    @JoinColumn(name = "kanban_column_id")
    private KanbanColumn kanbanColumn;
}
