package ru.manager.ProgectManager.controllers.kanban;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import ru.manager.ProgectManager.DTO.request.NameRequest;
import ru.manager.ProgectManager.DTO.request.kanban.CreateKanbanElementRequest;
import ru.manager.ProgectManager.DTO.request.kanban.TransportElementRequest;
import ru.manager.ProgectManager.DTO.response.ErrorResponse;
import ru.manager.ProgectManager.DTO.response.IdResponse;
import ru.manager.ProgectManager.DTO.response.kanban.KanbanElementContentResponse;
import ru.manager.ProgectManager.DTO.response.kanban.KanbanElements;
import ru.manager.ProgectManager.components.ErrorResponseEntityConfigurator;
import ru.manager.ProgectManager.entitys.kanban.KanbanColumn;
import ru.manager.ProgectManager.entitys.kanban.KanbanElement;
import ru.manager.ProgectManager.enums.ElementStatus;
import ru.manager.ProgectManager.enums.Errors;
import ru.manager.ProgectManager.enums.SearchElementType;
import ru.manager.ProgectManager.exception.IncorrectStatusException;
import ru.manager.ProgectManager.services.kanban.KanbanElementAttributesService;
import ru.manager.ProgectManager.services.kanban.KanbanElementService;
import ru.manager.ProgectManager.services.user.UserService;

import javax.validation.Valid;
import java.security.Principal;
import java.time.format.DateTimeParseException;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;

@RestController
@RequiredArgsConstructor
@RequestMapping("/users/kanban/element")
@Tag(name = "Манипуляции с элементами канбан-доски")
public class KanbanElementController {
    private final KanbanElementService kanbanElementService;
    private final KanbanElementAttributesService attributesService;
    private final ErrorResponseEntityConfigurator entityConfigurator;
    private final UserService userService;

    @Operation(summary = "Получение элемента канбана", description = "Получение полной информации об элементе канбана")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "404", description = "Указанного элемента не существует", content = {
                    @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))
            }),
            @ApiResponse(responseCode = "403", description = "Запрашивающий пользователь не имеет доступа к ресурсу",
                    content = {
                            @Content(mediaType = "application/json",
                                    schema = @Schema(implementation = ErrorResponse.class))
                    }),
            @ApiResponse(responseCode = "200", content = {
                    @Content(mediaType = "application/json",
                            schema = @Schema(implementation = KanbanElementContentResponse.class))
            })
    })
    @GetMapping()
    public ResponseEntity<?> getContent(@RequestParam long elementId, Principal principal) {
        try {
            String login = principal.getName();
            Optional<KanbanElementContentResponse> content = kanbanElementService
                    .getContentFromElement(elementId, login);
            if (content.isPresent()) {
                return ResponseEntity.ok(content.get());
            } else {
                return new ResponseEntity<>(HttpStatus.FORBIDDEN);
            }
        } catch (NoSuchElementException e) {
            return new ResponseEntity<>(new ErrorResponse(Errors.NO_SUCH_SPECIFIED_ELEMENT), HttpStatus.NOT_FOUND);
        }
    }

    @Operation(summary = "Добавление элемента")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "404", description = "Указанной колонки не существует", content = {
                    @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))
            }),
            @ApiResponse(responseCode = "403", description = "Пользователь не имеет доступа к ресурсу"),
            @ApiResponse(responseCode = "400", description = "Попытка создать элемент с пустым именем", content = {
                    @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))
            }),
            @ApiResponse(responseCode = "200", description = "Добавленный элемент", content = {
                    @Content(mediaType = "application/json",
                            schema = @Schema(implementation = IdResponse.class))
            })
    })
    @PostMapping
    public ResponseEntity<?> addElement(@RequestBody @Valid CreateKanbanElementRequest request,
                                        BindingResult bindingResult, Principal principal) {
        if (bindingResult.hasErrors()) {
            return entityConfigurator.createErrorResponse(bindingResult);
        } else {
            try {
                String login = principal.getName();
                Optional<KanbanElement> element = kanbanElementService.addElement(request, login);
                if (element.isPresent()) {
                    return ResponseEntity.ok(new IdResponse(element.get().getId()));
                } else {
                    return new ResponseEntity<>(HttpStatus.FORBIDDEN);
                }
            } catch (NoSuchElementException e) {
                return new ResponseEntity<>(new ErrorResponse(Errors.NO_SUCH_SPECIFIED_COLUMN), HttpStatus.NOT_FOUND);
            }
        }
    }

    @Operation(summary = "Поиск элемента", description = "Доступен поиск по названию и тегу элемента")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "404", description = "Указанного канбана не существует", content = {
                    @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))
            }),
            @ApiResponse(responseCode = "403", description = "Пользователь не имеет доступа к ресурсу"),
            @ApiResponse(responseCode = "200", description = "Список найденных элементов", content = {
                    @Content(mediaType = "application/json",
                            schema = @Schema(implementation = KanbanElements.class))
            })
    })
    @GetMapping("/find")
    public ResponseEntity<?> findElementsByName(@RequestParam @Parameter(description = "Идентиикатор канбана") long id,
                                                @RequestParam SearchElementType type,
                                                @RequestParam ElementStatus status, @RequestParam String name,
                                                @RequestParam int pageIndex, @RequestParam int rowCount,
                                                Principal principal) {
        try {
            String login = principal.getName();
            Optional<Set<KanbanElement>> elements = kanbanElementService.findElements(id, type, name, status, login);
            if (elements.isPresent()) {
                return ResponseEntity.ok(new KanbanElements(elements.get(), pageIndex, rowCount,
                        userService.findZoneIdForThisUser(login)));
            } else {
                return new ResponseEntity<>(HttpStatus.FORBIDDEN);
            }
        } catch (NoSuchElementException e) {
            return new ResponseEntity<>(new ErrorResponse(Errors.NO_SUCH_SPECIFIED_KANBAN), HttpStatus.NOT_FOUND);
        }
    }

    @Operation(summary = "Переименование элемента")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "404", description = "Попытка обращения к несуществующему элементу", content = {
                    @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))
            }),
            @ApiResponse(responseCode = "403", description = "Пользователь не имеет доступа к ресурсу"),
            @ApiResponse(responseCode = "400", description = "Попытка присвоения элементу пустого имени", content = {
                    @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))
            }),
            @ApiResponse(responseCode = "200", description = "Элемент успешно изменён"),
            @ApiResponse(responseCode = "410",
                    description = "Операция недоступна, поскольку элемент перемещён в корзину", content = {
                    @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))
            })
    })
    @PutMapping("/rename")
    public ResponseEntity<?> rename(@RequestParam long id, @RequestBody @Valid NameRequest request,
                                         BindingResult bindingResult, Principal principal) {
        if (bindingResult.hasErrors()) {
            return new ResponseEntity<>(new ErrorResponse(Errors.NAME_MUST_BE_CONTAINS_VISIBLE_SYMBOLS),
                    HttpStatus.BAD_REQUEST);
        } else {
            try {
                if (kanbanElementService.rename(id, request.getName(), principal.getName())) {
                    return new ResponseEntity<>(HttpStatus.OK);
                } else {
                    return new ResponseEntity<>(HttpStatus.FORBIDDEN);
                }
            } catch (NoSuchElementException e) {
                return new ResponseEntity<>(new ErrorResponse(Errors.NO_SUCH_SPECIFIED_ELEMENT), HttpStatus.NOT_FOUND);
            } catch (IncorrectStatusException e) {
                return new ResponseEntity<>(new ErrorResponse(Errors.INCORRECT_STATUS_ELEMENT_FOR_THIS_ACTION),
                        HttpStatus.GONE);
            }
        }
    }

    @Operation(summary = "Изменение даты, прикреплённой к элементу")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "404", description = "Попытка обращения к несуществующему элементу", content = {
                    @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))
            }),
            @ApiResponse(responseCode = "403", description = "Пользователь не имеет доступа к ресурсу"),
            @ApiResponse(responseCode = "400", description = "Неверный формат даты", content = {
                    @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))
            }),
            @ApiResponse(responseCode = "200", description = "Элемент успешно изменён"),
            @ApiResponse(responseCode = "410",
                    description = "Операция недоступна, поскольку элемент перемещён в корзину", content = {
                    @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))
            })
    })
    @PutMapping("/date")
    public ResponseEntity<?> editDate(@RequestParam long id, @RequestParam String date, Principal principal) {
        try {
            if (kanbanElementService.editDate(id, date, principal.getName())) {
                return new ResponseEntity<>(HttpStatus.OK);
            } else {
                return new ResponseEntity<>(HttpStatus.FORBIDDEN);
            }
        } catch (NoSuchElementException e) {
            return new ResponseEntity<>(new ErrorResponse(Errors.NO_SUCH_SPECIFIED_ELEMENT), HttpStatus.NOT_FOUND);
        } catch (IncorrectStatusException e) {
            return new ResponseEntity<>(new ErrorResponse(Errors.INCORRECT_STATUS_ELEMENT_FOR_THIS_ACTION),
                    HttpStatus.GONE);
        } catch (DateTimeParseException e) {
            return new ResponseEntity<>(new ErrorResponse(Errors.WRONG_DATE_FORMAT), HttpStatus.BAD_REQUEST);
        }
    }

    @Operation(summary = "Удаление даты, прикреплённой к элементу")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "404", description = "Попытка обращения к несуществующему элементу", content = {
                    @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))
            }),
            @ApiResponse(responseCode = "403", description = "Пользователь не имеет доступа к ресурсу"),
            @ApiResponse(responseCode = "200", description = "Элемент успешно изменён"),
            @ApiResponse(responseCode = "410",
                    description = "Операция недоступна, поскольку элемент перемещён в корзину", content = {
                    @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))
            })
    })
    @DeleteMapping("/date")
    public ResponseEntity<?> dropDate(@RequestParam long id, Principal principal) {
        try {
            if (kanbanElementService.dropDate(id, principal.getName())) {
                return new ResponseEntity<>(HttpStatus.OK);
            } else {
                return new ResponseEntity<>(HttpStatus.FORBIDDEN);
            }
        } catch (NoSuchElementException e) {
            return new ResponseEntity<>(new ErrorResponse(Errors.NO_SUCH_SPECIFIED_ELEMENT), HttpStatus.NOT_FOUND);
        } catch (IncorrectStatusException e) {
            return new ResponseEntity<>(new ErrorResponse(Errors.INCORRECT_STATUS_ELEMENT_FOR_THIS_ACTION),
                    HttpStatus.GONE);
        }
    }

    @Operation(summary = "Изменение контента элемента")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "404", description = "Попытка обращения к несуществующему элементу", content = {
                    @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))
            }),
            @ApiResponse(responseCode = "403", description = "Пользователь не имеет доступа к ресурсу"),
            @ApiResponse(responseCode = "200", description = "Элемент успешно изменён"),
            @ApiResponse(responseCode = "410",
                    description = "Операция недоступна, поскольку элемент перемещён в корзину", content = {
                    @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))
            })
    })
    @PutMapping("/content")
    public ResponseEntity<?> editContent(@RequestParam long id, @RequestParam String content, Principal principal) {
        try {
            if (kanbanElementService.editContent(id, content, principal.getName())) {
                return new ResponseEntity<>(HttpStatus.OK);
            } else {
                return new ResponseEntity<>(HttpStatus.FORBIDDEN);
            }
        } catch (NoSuchElementException e) {
            return new ResponseEntity<>(new ErrorResponse(Errors.NO_SUCH_SPECIFIED_ELEMENT), HttpStatus.NOT_FOUND);
        } catch (IncorrectStatusException e) {
            return new ResponseEntity<>(new ErrorResponse(Errors.INCORRECT_STATUS_ELEMENT_FOR_THIS_ACTION),
                    HttpStatus.GONE);
        }
    }

    @Operation(summary = "Удаление элемента")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "404", description = "Обращение к несуществующему элементу", content = {
                    @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))
            }),
            @ApiResponse(responseCode = "403", description = "Пользователь не имеет доступа к ресурсу"),
            @ApiResponse(responseCode = "200", description = "Элемент успешно удалён"),
            @ApiResponse(responseCode = "410",
                    description = "Операция недоступна, поскольку элемент уже в корзине", content = {
                    @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))
            })
    })
    @DeleteMapping()
    public ResponseEntity<?> removeElement(@RequestParam long id, Principal principal) {
        try {
            String login = principal.getName();
            Optional<KanbanColumn> column = kanbanElementService.utilizeElementFromUser(id, login);
            if (column.isPresent()) {
                return new ResponseEntity<>(HttpStatus.OK);
            } else {
                return new ResponseEntity<>(HttpStatus.FORBIDDEN);
            }
        } catch (NoSuchElementException e) {
            return new ResponseEntity<>(new ErrorResponse(Errors.NO_SUCH_SPECIFIED_ELEMENT), HttpStatus.NOT_FOUND);
        } catch (IncorrectStatusException e) {
            return new ResponseEntity<>(new ErrorResponse(Errors.INCORRECT_STATUS_ELEMENT_FOR_THIS_ACTION),
                    HttpStatus.GONE);
        }
    }

    @Operation(summary = "Перемещение элемента канбана",
            description = "Изменение местоположения элемента на указанный порядковый номер в указанном столбце")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "404", description = "Неверные идентификаторы колонки или элемента", content = {
                    @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))
            }),
            @ApiResponse(responseCode = "403", description = "Пользователь не имеет доступа к данному ресурсу"),
            @ApiResponse(responseCode = "400", description = "Введённый желаемый порядковый номер элемента недопостим",
                    content = {
                            @Content(mediaType = "application/json",
                                    schema = @Schema(implementation = ErrorResponse.class))
                    }),
            @ApiResponse(responseCode = "200", description = "Указанный элемент успешно перенесён"),
            @ApiResponse(responseCode = "410",
                    description = "Операция недоступна, поскольку элемент перемещён в корзину или архив", content = {
                    @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))
            })
    })
    @PutMapping("/transport")
    public ResponseEntity<?> transportElement(@RequestBody @Valid TransportElementRequest transportElementRequest,
                                              BindingResult bindingResult, Principal principal) {
        if (bindingResult.hasErrors()) {
            return entityConfigurator.createErrorResponse(bindingResult);
        } else {
            try {
                if (kanbanElementService.transportElement(transportElementRequest, principal.getName())) {
                    return new ResponseEntity<>(HttpStatus.OK);
                }
                return new ResponseEntity<>(HttpStatus.FORBIDDEN);
            } catch (NoSuchElementException e) {
                return new ResponseEntity<>(new ErrorResponse(Errors.NO_SUCH_SPECIFIED_ELEMENT_OR_COLUMN),
                        HttpStatus.NOT_FOUND);
            } catch (IllegalArgumentException e) {
                return new ResponseEntity<>(new ErrorResponse(Errors.INDEX_MORE_COLLECTION_SIZE),
                        HttpStatus.BAD_REQUEST);
            } catch (IncorrectStatusException e) {
                return new ResponseEntity<>(new ErrorResponse(Errors.INCORRECT_STATUS_ELEMENT_FOR_THIS_ACTION),
                        HttpStatus.GONE);
            }
        }
    }

    @Operation(summary = "Добавление существующего в этом канбане тега к указанному элементу")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "404", description = "Обращение к несуществующему элементу или тегу",
                    content = {
                            @Content(mediaType = "application/json",
                                    schema = @Schema(implementation = ErrorResponse.class))
                    }),
            @ApiResponse(responseCode = "403", description = "Пользователь не имеет доступа к данному ресурсу"),
            @ApiResponse(responseCode = "410", description = "Элемент перемещён в корзину и недоступен для изменеия",
                    content = {
                            @Content(mediaType = "application/json",
                                    schema = @Schema(implementation = ErrorResponse.class))
                    }),
            @ApiResponse(responseCode = "200", description = "Тег успешно добавлен"),
    })
    @PostMapping("/tag")
    public ResponseEntity<?> addTag(@RequestParam long elementId, @RequestParam long tagId, Principal principal) {
        try {
            if (attributesService.addTag(elementId, tagId, principal.getName())) {
                return new ResponseEntity<>(HttpStatus.OK);
            } else {
                return new ResponseEntity<>(HttpStatus.FORBIDDEN);
            }
        } catch (NoSuchElementException e) {
            return new ResponseEntity<>(new ErrorResponse(Errors.NO_SUCH_SPECIFIED_ELEMENT), HttpStatus.NOT_FOUND);
        } catch (IllegalArgumentException e) {
            return new ResponseEntity<>(new ErrorResponse(Errors.NO_SUCH_SPECIFIED_TAG), HttpStatus.NOT_FOUND);
        } catch (IncorrectStatusException e) {
            return new ResponseEntity<>(new ErrorResponse(Errors.INCORRECT_STATUS_ELEMENT_FOR_THIS_ACTION),
                    HttpStatus.GONE);
        }
    }

    @Operation(summary = "Удаление тега из элемента")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "404", description = "Обращение к несуществующему элементу",
                    content = {
                            @Content(mediaType = "application/json",
                                    schema = @Schema(implementation = ErrorResponse.class))
                    }),
            @ApiResponse(responseCode = "403", description = "Пользователь не имеет доступа к данному ресурсу"),
            @ApiResponse(responseCode = "410", description = "Элемент перемещён в корзину и недоступен для изменеия",
                    content = {
                            @Content(mediaType = "application/json",
                                    schema = @Schema(implementation = ErrorResponse.class))
                    }),
            @ApiResponse(responseCode = "200", description = "Тег успешно удалён"),
    })
    @DeleteMapping("/tag")
    public ResponseEntity<?> removeTag(@RequestParam long elementId, @RequestParam long tagId, Principal principal) {
        try {
            if (attributesService.removeTag(elementId, tagId, principal.getName())) {
                return new ResponseEntity<>(HttpStatus.OK);
            } else {
                return new ResponseEntity<>(HttpStatus.FORBIDDEN);
            }
        } catch (NoSuchElementException e) {
            return new ResponseEntity<>(new ErrorResponse(Errors.NO_SUCH_SPECIFIED_ELEMENT), HttpStatus.NOT_FOUND);
        } catch (IncorrectStatusException e) {
            return new ResponseEntity<>(new ErrorResponse(Errors.INCORRECT_STATUS_ELEMENT_FOR_THIS_ACTION),
                    HttpStatus.GONE);
        }
    }
}
