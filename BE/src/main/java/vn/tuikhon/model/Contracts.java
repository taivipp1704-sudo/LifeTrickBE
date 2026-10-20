package vn.tuikhon.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

public final class Contracts {
  public record Register(
      @Email @NotBlank @Size(max = 254) String email,
      @NotBlank @Size(min = 12, max = 72) String password,
      @NotBlank @Size(max = 80) String name) {}

  public record Login(
      @Email @NotBlank @Size(max = 254) String email, @NotBlank @Size(max = 72) String password) {}

  public record Step(
      @NotNull @Size(max = 150) String title, @NotNull @Size(max = 2500) String body) {}

  public record Content(
      @NotNull @Size(max = 160) String title,
      @NotBlank @Pattern(regexp = "life|survival|kitchen|firstaid|travel|tech|diy") String category,
      @NotNull @Size(max = 2500) String description,
      @NotNull @Size(max = 20) List<@NotBlank @Size(max = 100) String> tools,
      @NotNull @Size(min = 3, max = 3) List<@NotNull @Valid Step> steps,
      @NotNull @Size(max = 2500) String warning,
      @NotNull @Size(max = 500) String sourceTitle,
      @NotNull @Pattern(regexp = "(?:|https://[^\\s<>]{3,480})") String sourceUrl,
      @NotBlank @Pattern(regexp = "water|fire|watch|jar|bottle|cable|shelter|medical") String model,
      @NotBlank @Pattern(regexp = "forest|camp|mountain|kitchen|bottle|desk") String image) {}

  public record Draft(
      @NotNull @Valid Content content,
      @Pattern(regexp = "[a-f0-9-]{36}") String mediaId,
      @Min(0) int version) {}

  public record Revision(@Min(0) int version) {}

  public record Decision(
      @Pattern(regexp = "APPROVE|REQUEST_CHANGES|REJECT") String decision,
      @NotBlank @Size(min = 10, max = 2500) String notes,
      @NotBlank @Size(max = 2000) String evidence,
      boolean scienceChecked,
      boolean safetyChecked,
      boolean materialsChecked,
      @Min(0) int version) {}

  public record RoleChange(
      @Pattern(regexp = "MEMBER|REVIEWER|ADMIN") String role, boolean active) {}

  public record Profile(@NotBlank @Size(max = 80) String name) {}

  public record PasswordChange(
      @NotBlank @Size(max = 72) String oldPassword,
      @NotBlank @Size(min = 12, max = 72) String newPassword) {}

  public record Feedback(@NotBlank @Size(max = 1500) String text) {}

  public record Progress(boolean completed) {}
}
