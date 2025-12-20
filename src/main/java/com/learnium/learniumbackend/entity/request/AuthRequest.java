package com.learnium.learniumbackend.entity.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class AuthRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotNull
    @Email(message = "Invalid email format")
    @Schema(description = "user email", example = "test@test.com")
    private String email;

    @NotNull
    @Size(min = 8, message = "Password must be at least 8 characters")
    @Schema(description = "user password", example = "strongpassword123")
    private String password;

    @NotNull
    @Schema(description = "user display name", example = "John Doe")
    private String displayName;

    @NotNull
    @Schema(description = "user grade level", example = "10")
    private Integer gradeId;
}

