package com.blaie.blaie_be.auth.application;

import com.blaie.blaie_be.auth.application.command.LoginLocalCommand;
import com.blaie.blaie_be.auth.application.command.RegisterLocalCommand;
import com.blaie.blaie_be.auth.application.command.UpdatePasswordCommand;
import com.blaie.blaie_be.auth.application.command.UpdateUsernameCommand;
import com.blaie.blaie_be.auth.application.port.GoogleOAuthProfile;
import com.blaie.blaie_be.auth.application.result.AuthUserResult;
import com.blaie.blaie_be.auth.application.result.WebAuthResult;

public interface AuthService {
    WebAuthResult registerLocal(RegisterLocalCommand command, String userAgent);

    WebAuthResult loginLocal(LoginLocalCommand command, String userAgent);

    WebAuthResult loginGoogle(GoogleOAuthProfile profile, String userAgent);

    WebAuthResult refreshWeb(String refreshToken, String userAgent);

    void logoutWeb(String refreshToken);

    AuthUserResult currentUser();

    AuthUserResult updateUsername(UpdateUsernameCommand command);

    AuthUserResult updatePassword(UpdatePasswordCommand command);
}
