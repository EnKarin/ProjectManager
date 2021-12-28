package ru.manager.ProgectManager.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import ru.manager.ProgectManager.DTO.request.PhotoDTO;
import ru.manager.ProgectManager.DTO.request.RefreshUserDTO;
import ru.manager.ProgectManager.DTO.response.AllUserDataResponse;
import ru.manager.ProgectManager.DTO.response.PublicUserDataResponse;
import ru.manager.ProgectManager.components.JwtProvider;
import ru.manager.ProgectManager.entitys.User;
import ru.manager.ProgectManager.services.UserService;

import javax.validation.Valid;
import java.io.IOException;
import java.util.Optional;

@RestController
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;
    private final JwtProvider jwtProvider;

    @GetMapping("/users/user")
    public ResponseEntity<?> findAllData(@RequestParam long id) {
        Optional<User> user;
        if (id == -1) { //about yourself
            user = userService.findByUsername(jwtProvider.getLoginFromToken());
            if (user.isPresent()) {
                return ResponseEntity.ok(new AllUserDataResponse(user.get()));
            } else {
                return new ResponseEntity<>("No such specified user", HttpStatus.BAD_REQUEST);
            }
        } else {
            user = userService.findById(id);
            if (user.isPresent()) {
                return ResponseEntity.ok(new PublicUserDataResponse(user.get()));
            } else {
                return new ResponseEntity<>("No such specified user", HttpStatus.BAD_REQUEST);
            }
        }
    }

    @PutMapping("users/user")
    public ResponseEntity<?> refreshMainData(@RequestBody @Valid RefreshUserDTO userDTO, BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            StringBuilder stringBuilder = new StringBuilder();
            bindingResult.getAllErrors().forEach(e -> stringBuilder.append(e.getDefaultMessage()).append("; "));
            return new ResponseEntity<>(stringBuilder.toString(), HttpStatus.NOT_ACCEPTABLE);
        }
        userService.refreshUserData(jwtProvider.getLoginFromToken(), userDTO);
        return new ResponseEntity<>(HttpStatus.OK);
    }

    @PostMapping("users/user/photo")
    public ResponseEntity<?> setPhoto(@ModelAttribute PhotoDTO photoDTO) {
        try {
            userService.setPhoto(jwtProvider.getLoginFromToken(), photoDTO.getFile());
            return new ResponseEntity<>(HttpStatus.OK);
        } catch (IOException e) {
            return new ResponseEntity<>(e.getMessage(), HttpStatus.NOT_ACCEPTABLE);
        }
    }
}
