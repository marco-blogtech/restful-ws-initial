package com.edicom.webservice.rest.restfulws.user;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

import com.edicom.webservice.rest.restfulws.jpa.PostRepository;
import com.edicom.webservice.rest.restfulws.jpa.UserRepository;
import com.edicom.webservice.rest.restfulws.post.Post;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ser.FilterProvider;
import com.fasterxml.jackson.databind.ser.impl.SimpleBeanPropertyFilter;
import com.fasterxml.jackson.databind.ser.impl.SimpleFilterProvider;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.github.fge.jsonpatch.JsonPatch;
import com.github.fge.jsonpatch.JsonPatchException;
import jakarta.validation.Valid;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.server.mvc.WebMvcLinkBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.json.MappingJacksonValue;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.Optional;

@RestController
public class UserResource {

    private UserRepository userRepository;
    private PostRepository postRepository;

    private ObjectMapper objectMapper = new ObjectMapper();

    public UserResource(UserRepository userRepository, PostRepository postRepository){
        this.userRepository = userRepository;
        this.postRepository = postRepository;
        objectMapper.registerModule(new JavaTimeModule());
    }

    @GetMapping("/users")
    public MappingJacksonValue retrieveAllUsers(){
        //First we find all users and create a MappingJacksonValue
        MappingJacksonValue mappingJacksonValue = new MappingJacksonValue(userRepository.findAll());
        //We do not want to return user password or posts, so we only return id, user_name and birth_date
        SimpleBeanPropertyFilter filter = SimpleBeanPropertyFilter.filterOutAllExcept("id","user_name","birth_date","posts");
        FilterProvider filters = new SimpleFilterProvider().addFilter("userFilter", filter);
        mappingJacksonValue.setFilters(filters);
        return mappingJacksonValue;
    }

    @GetMapping("/users/{id}")
    public MappingJacksonValue retrieveUser(@PathVariable int id){
        //We find a user
        Optional<User> user = userRepository.findById(id);

        //If optional is empty, UserNotFoundException
        if (user.isEmpty()){
            throw new UserNotFoundException("User not found - id:"+id);
        }

        //Get the user from the optional
        EntityModel<User> entityModel = EntityModel.of(user.get());

        //Get the link to localhost:8080/users
        WebMvcLinkBuilder link = linkTo(methodOn(this.getClass()).retrieveAllUsers());
        entityModel.add(link.withRel("all-users"));

        //Again filter the user
        MappingJacksonValue mappingJacksonValue = new MappingJacksonValue(entityModel);
        SimpleBeanPropertyFilter filter = SimpleBeanPropertyFilter.filterOutAllExcept("id","user_name","birth_date","posts");
        FilterProvider filters = new SimpleFilterProvider().addFilter("userFilter", filter);
        mappingJacksonValue.setFilters(filters);
        return mappingJacksonValue;
    }

    @PostMapping("/users")
    public ResponseEntity<MappingJacksonValue> createUser(@Valid @RequestBody User user){
        //We save the user and get its id
        User savedUser = userRepository.save(user);
        MappingJacksonValue mappingJacksonValue = new MappingJacksonValue(savedUser);
        SimpleBeanPropertyFilter filter = SimpleBeanPropertyFilter.serializeAllExcept();
        FilterProvider filters = new SimpleFilterProvider().addFilter("userFilter", filter);
        mappingJacksonValue.setFilters(filters);
        //Get the uri to the resource
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(savedUser.getId()).toUri();
        //Return 201 Status Code with location=uriToResource
        return ResponseEntity.created(location).body(mappingJacksonValue);
    }

    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping("/users/{id}")
    public void deleteUser(@PathVariable int id) {
        Optional<User> user = userRepository.findById(id);

        //If optional is empty, UserNotFoundException
        if (user.isEmpty()) {
            throw new UserNotFoundException("User not found - id:" + id);
        }
        //Delete user
        userRepository.deleteById(id);
    }

    @PutMapping("/users/{id}")
    public ResponseEntity<MappingJacksonValue> updateUser(@PathVariable int id, @Valid @RequestBody User updateUser){
        //We find a user
        Optional<User> user = userRepository.findById(id);

        User savedUser = null;
        //If optional is empty, we create it and return 201
        if (user.isEmpty()){
            //We save the user and get its id
            savedUser = userRepository.save(updateUser);
            //Filtering
            MappingJacksonValue mappingJacksonValue = new MappingJacksonValue(savedUser);
            SimpleBeanPropertyFilter filter = SimpleBeanPropertyFilter.filterOutAllExcept("id","user_name","birth_date","posts");
            FilterProvider filters = new SimpleFilterProvider().addFilter("userFilter", filter);
            mappingJacksonValue.setFilters(filters);
            //Get the uri to the resource
            URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(savedUser.getId()).toUri();
            //Return 201 Status Code with location=uriToResource
            return ResponseEntity.created(location).body(mappingJacksonValue);
        }else{
            //We must set ID
            updateUser.setId(id);
            //We update the user and get its id
            savedUser = userRepository.save(updateUser);
            //Filtering
            MappingJacksonValue mappingJacksonValue = new MappingJacksonValue(savedUser);
            SimpleBeanPropertyFilter filter = SimpleBeanPropertyFilter.filterOutAllExcept("id","user_name","birth_date","posts");
            FilterProvider filters = new SimpleFilterProvider().addFilter("userFilter", filter);
            mappingJacksonValue.setFilters(filters);
            //Return empty ok() 200
            return ResponseEntity.ok().build();
        }
    }

    //Partial edit one user
    @PatchMapping(value="/users/{id}", consumes = {"application/merge-patch+json"})
    public ResponseEntity<MappingJacksonValue> patchUser(@PathVariable int id, @RequestBody JsonPatch jsonPatch) throws JsonPatchException, JsonProcessingException {
        //We find a user
        Optional<User> user = userRepository.findById(id);

        //If optional is empty, UserNotFoundException
        if (user.isEmpty()){
            throw new UserNotFoundException("User not found - id:"+id);
        }else{
            //Apply patch to user
            User patchedUser = applyPatchToUser(user.get(), jsonPatch);
            //We update the user and get it
            User savedUser = userRepository.save(patchedUser);
            MappingJacksonValue mappingJacksonValue = new MappingJacksonValue(savedUser);
            SimpleBeanPropertyFilter filter = SimpleBeanPropertyFilter.filterOutAllExcept("id","user_name","birth_date","posts");
            FilterProvider filters = new SimpleFilterProvider().addFilter("userFilter", filter);
            mappingJacksonValue.setFilters(filters);
            //We must return 200 OK
            return ResponseEntity.ok().body(mappingJacksonValue);
        }
    }

    // Methods for Post

    //Get all posts for specified user
    @GetMapping("/users/{id}/posts")
    public List<Post> retrievePostsForUser(@PathVariable int id){
        //Get the user
        Optional<User> user = userRepository.findById(id);

        //If optional is empty, UserNotFoundException
        if (user.isEmpty()){
            throw new UserNotFoundException("User not found - id:"+id);
        }

        //We can get all posts from retrieved user
        return user.get().getPosts();
    }

    //Create a new post for specified user
    @PostMapping("/users/{id}/posts")
    public ResponseEntity<Post> createPostForUser(@PathVariable int id, @Valid @RequestBody Post post){
        //Get the user
        Optional<User> user = userRepository.findById(id);

        //If optional is empty, UserNotFoundException
        if (user.isEmpty()){
            throw new UserNotFoundException("User not found - id:"+id);
        }

        //Set the user to Post
        post.setUser(user.get());

        //We save the post
        Post savedPost = postRepository.save(post);

        //Get resource uri
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(savedPost.getId()).toUri();

        //Return 201 Status + uri in location
        return ResponseEntity.created(location).body(savedPost);
    }

    //Utilities

    public User applyPatchToUser(User targetUser, JsonPatch patch) throws JsonPatchException, JsonProcessingException {
        SimpleBeanPropertyFilter filter = SimpleBeanPropertyFilter.filterOutAllExcept("id","user_name","birth_date","posts");
        FilterProvider filters = new SimpleFilterProvider().addFilter("userFilter", filter);
        objectMapper.setFilterProvider(filters);
        JsonNode patched = patch.apply(objectMapper.convertValue(targetUser, JsonNode.class));
        return objectMapper.treeToValue(patched, User.class);
    }
}
