package ru.manager.ProgectManager.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.annotation.*;
import ru.manager.ProgectManager.DTO.request.NameRequest;
import ru.manager.ProgectManager.DTO.request.PhotoDTO;
import ru.manager.ProgectManager.DTO.response.ErrorResponse;
import ru.manager.ProgectManager.DTO.response.ProjectResponse;
import ru.manager.ProgectManager.components.JwtProvider;
import ru.manager.ProgectManager.components.PhotoCompressor;
import ru.manager.ProgectManager.entitys.Project;
import ru.manager.ProgectManager.services.ProjectService;

import javax.validation.Valid;
import java.io.IOException;
import java.util.Collections;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
@RequestMapping("/users")
public class ProjectController {
    private final ProjectService projectService;
    private final JwtProvider provider;
    private final PhotoCompressor compressor;

    @PostMapping("/project")
    public ResponseEntity<?> addProject(@RequestBody @Valid NameRequest requestDTO, BindingResult bindingResult){
        if(bindingResult.hasErrors()){
            return new ResponseEntity<>(new ErrorResponse(bindingResult.getAllErrors().stream()
                    .map(ObjectError::getDefaultMessage)
                    .collect(Collectors.toList())),
                    HttpStatus.NOT_ACCEPTABLE);
        } else{
            return ResponseEntity.ok(projectService.addProject(requestDTO, provider.getLoginFromToken()));
        }
    }

    @GetMapping("/project")
    public ResponseEntity<?> findProject(@RequestParam long id){
        try {
            Optional<Project> project = projectService.findProject(id, provider.getLoginFromToken());
            if (project.isPresent()) {
                return ResponseEntity.ok(new ProjectResponse(project.get()));
            } else {
                return new ResponseEntity<>(HttpStatus.FORBIDDEN);
            }
        } catch (NoSuchElementException e){
            return new ResponseEntity<>(new ErrorResponse(Collections.singletonList("Project: No such specified project")),
                    HttpStatus.BAD_REQUEST);
        }
    }

    @PutMapping("/project")
    public ResponseEntity<?> setName(@RequestParam long id, @RequestBody @Valid NameRequest requestDTO,
                                     BindingResult bindingResult){
        if(bindingResult.hasErrors()){
            return new ResponseEntity<>(new ErrorResponse(bindingResult.getAllErrors().stream()
                    .map(ObjectError::getDefaultMessage)
                    .collect(Collectors.toList())),
                    HttpStatus.NOT_ACCEPTABLE);
        } else{
            return ResponseEntity.ok(projectService.setName(id, requestDTO));
        }
    }

    @PostMapping("/project/photo")
    public ResponseEntity<?> setPhoto(@RequestParam long id, @ModelAttribute PhotoDTO photoDTO){
        try{
            if(projectService.setPhoto(id, compressor.compress(photoDTO.getFile()))) {
                return new ResponseEntity<>(HttpStatus.OK);
            } else{
                return new ResponseEntity<>(new ErrorResponse(Collections.singletonList("Project: No such specified project")),
                        HttpStatus.BAD_REQUEST);
            }
        } catch (IOException e){
            return new ResponseEntity<>(new ErrorResponse(Collections.singletonList(e.getMessage())),
                    HttpStatus.NOT_ACCEPTABLE);
        }
    }

    @DeleteMapping("/project")
    public ResponseEntity<?> deleteProject(@RequestParam long id){
        try {
            if(projectService.deleteProject(id, provider.getLoginFromToken())){
                return new ResponseEntity<>(HttpStatus.OK);
            }
            return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        } catch (NoSuchElementException e){
            return new ResponseEntity<>(new ErrorResponse(Collections.singletonList("Project: Not such specified project")),
                    HttpStatus.BAD_REQUEST);
        } catch (AssertionError e){
            return new ResponseEntity<>(new ErrorResponse(Collections.singletonList("User: Not such specified user")),
                    HttpStatus.BAD_REQUEST);
        }
    }
}
