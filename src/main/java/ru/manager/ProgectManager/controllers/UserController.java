package ru.manager.ProgectManager.controllers;

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
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.annotation.*;
import ru.manager.ProgectManager.DTO.request.PhotoDTO;
import ru.manager.ProgectManager.DTO.request.RefreshUserDTO;
import ru.manager.ProgectManager.DTO.response.AllUserDataResponse;
import ru.manager.ProgectManager.DTO.response.ErrorResponse;
import ru.manager.ProgectManager.DTO.response.PublicUserDataResponse;
import ru.manager.ProgectManager.components.JwtProvider;
import ru.manager.ProgectManager.components.PhotoCompressor;
import ru.manager.ProgectManager.entitys.User;
import ru.manager.ProgectManager.enums.Errors;
import ru.manager.ProgectManager.services.UserService;

import javax.validation.Valid;
import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
@RequestMapping("/users")
@Tag(name = "Управление аккаунтом пользователя")
public class UserController {
    private final UserService userService;
    private final JwtProvider jwtProvider;
    private final PhotoCompressor compressor;

    @Operation(summary = "Предоставление информации о пользователе",
            description = "Позволяет предоставлять информацию как о своём аккаунте, так и о чужих")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "400", description = "Обращение к неуществующему пользователю", content = {
                    @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))
            }),
            @ApiResponse(responseCode = "200",
                    description = "Возвращение информации о своём профиле, " +
                            "при доступе к чужому аккаунту не выдаётся информация об его проектах",
                    content = {
                            @Content(mediaType = "application/json",
                                    schema = @Schema(implementation = AllUserDataResponse.class))
                    })
    })
    @GetMapping("/user")
    public ResponseEntity<?> findAllData(@RequestParam @Parameter(description = "Идентификатор пользователя," +
            " -1 для доступа к своему аккаунту") long id) {
        Optional<User> user;
        if (id == -1) { //about yourself
            user = userService.findByUsername(jwtProvider.getLoginFromToken());
            return ResponseEntity.ok(new AllUserDataResponse(user.get()));
        } else {
            user = userService.findById(id);
            if (user.isPresent()) {
                return ResponseEntity.ok(new PublicUserDataResponse(user.get()));
            } else {
                return new ResponseEntity<>(new ErrorResponse(Errors.NO_SUCH_SPECIFIED_USER),
                        HttpStatus.BAD_REQUEST);
            }
        }
    }

    @Operation(summary = "Изменение данных аккаунта")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "403", description = "Указан неверный пароль", content = {
                    @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))
            }),
            @ApiResponse(responseCode = "406", description = "Вводимые данные не приемлимы", content = {
                    @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))
            }),
            @ApiResponse(responseCode = "200", description = "Данные пользователя успешно изменены")
    })
    @PutMapping("/user")
    public ResponseEntity<?> refreshMainData(@RequestBody @Valid RefreshUserDTO userDTO, BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return new ResponseEntity<>(new ErrorResponse(bindingResult.getAllErrors().stream()
                    .map(ObjectError::getDefaultMessage)
                    .filter(Objects::nonNull)
                    .map(Integer::parseInt)
                    .collect(Collectors.toList())),
                    HttpStatus.NOT_ACCEPTABLE);
        }
        if (userService.refreshUserData(jwtProvider.getLoginFromToken(), userDTO)) {
            return new ResponseEntity<>(HttpStatus.OK);
        }
        return new ResponseEntity<>("Old password: incorrect password", HttpStatus.FORBIDDEN);
    }

    @Operation(summary = "Установление фотографии профиля", description = "Добавление или замена фотографии")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "406", description = "Файл не может быть корректно прочитан или обработан",
                    content = {
                            @Content(mediaType = "application/json",
                                    schema = @Schema(implementation = ErrorResponse.class))
                    }),
            @ApiResponse(responseCode = "200", description = "Фотография успешно сжата и сохранена")
    })
    @PostMapping("/user/photo")
    public ResponseEntity<?> setPhoto(@ModelAttribute PhotoDTO photoDTO) {
        try {
            userService.setPhoto(jwtProvider.getLoginFromToken(), compressor.compress(photoDTO.getFile()));
            return new ResponseEntity<>(HttpStatus.OK);
        } catch (IOException e) {
            return new ResponseEntity<>(new ErrorResponse(Errors.BAD_FILE),
                    HttpStatus.NOT_ACCEPTABLE);
        }
    }
}
