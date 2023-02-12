package com.bbb.songeoreum.api.controller;

import com.bbb.songeoreum.api.request.InsertUserReq;
import com.bbb.songeoreum.api.request.LoginReq;
import com.bbb.songeoreum.api.request.UpdateUserReq;
import com.bbb.songeoreum.api.response.*;
import com.bbb.songeoreum.api.service.AuthService;
import com.bbb.songeoreum.api.service.UserService;
import com.bbb.songeoreum.config.AppProperties;
import com.bbb.songeoreum.db.domain.User;
import com.bbb.songeoreum.exception.DuplicateException;
import com.bbb.songeoreum.exception.NotFoundException;
import com.bbb.songeoreum.exception.UnAuthorizedException;
import com.bbb.songeoreum.jwt.AuthToken;
import com.bbb.songeoreum.jwt.AuthTokenProvider;
import com.bbb.songeoreum.util.CookieUtil;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static com.bbb.songeoreum.db.repository.OAuth2AuthorizationRequestBasedOnCookieRepository.REFRESH_TOKEN;

@Slf4j
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
@Api(tags = {"사용자 API"}) // Swagger에 보여줄 명칭
public class UserController {

    private static final String SUCCESS = "success";
    private static final String FAIL = "fail";

    private final UserService userService;
    private final AuthService authService;

    private final AuthTokenProvider tokenProvider;
    private final AppProperties appProperties;

    /**
     * 카카오 로그인을 진행합니다.
     * @param code 카카오에게 받은 인가 코드
     * @return 성공 시 DB에 저장된 kakao user 정보를 {@code ResponseEntity} 로 반환합니다.
     * @throws NotFoundException
     * @throws IllegalArgumentException
     */
    @ApiOperation(value = "카카오 로그인")
    @GetMapping("/oauth2/kakao")
    public ResponseEntity<KakaoLoginRes> kakaoLogin(@RequestParam("code") String code, HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) throws NotFoundException, IllegalArgumentException {

        log.debug("카카오 user 로그인");

        String kakaoAccessToken = authService.getKakaoAccessToken(code);
        log.debug("카카오에서 accessToken 받아옴 : {}", kakaoAccessToken);
        ResponseEntity<KakaoLoginRes> kakaoLoginRes = authService.kakaoLogin(kakaoAccessToken, httpServletRequest, httpServletResponse);
        log.debug("카카오 로그인 user 닉네임 : {}", kakaoLoginRes.getBody().getNickname());
        return kakaoLoginRes;
    }

    /**
     * 이메일 중복체크
     * @param email 중복체크 요청한 이메일
     * @return 성공 시 성공메시지를 {@code ResponseEntity}로 반환합니다
     * @throws DuplicateException
     */
    @ApiOperation(value = "이메일 중복체크")
    @GetMapping("/signup/email/{email}")
    public ResponseEntity<SuccessRes> duplicateEmail(@PathVariable("email") String email) throws DuplicateException {

        log.debug("중복체크 요청 이메일 = {}", email);

        userService.duplicateEmail(email);

        HttpStatus httpStatus = HttpStatus.OK;
        SuccessRes successRes = SuccessRes.builder().message(SUCCESS).build();

        return new ResponseEntity<>(successRes, httpStatus);

    }

    /**
     * 닉네임 중복체크
     * @param nickname 중복체크 요청한 닉네임
     * @return 성공 시 성공메시지를 {@code ResponseEntity}로 반환합니다
     * @throws DuplicateException
     */
    @ApiOperation(value = "닉네임 중복체크")
    @GetMapping("/signup/nickname/{nickname}")
    public ResponseEntity<SuccessRes> duplicateNickname(@PathVariable("nickname") String nickname) throws DuplicateException {

        log.debug("중복체크 요청 닉네임 = {}", nickname);

        userService.duplicateNickname(nickname);

        HttpStatus httpStatus = HttpStatus.OK;
        SuccessRes successRes = SuccessRes.builder().message(SUCCESS).build();

        return new ResponseEntity<>(successRes, httpStatus);
    }

    /**
     * 회원 가입
     * @param insertUserReq 회원 가입 form에 사용자가 입력한 정보
     * @return 성공 시 성공메시지를 {@code ResponseEntity}로 반환합니다
     * @throws DuplicateException
     */
    @ApiOperation(value = "회원가입") // 해당 Api의 설명
    @PostMapping("/signup")
    public ResponseEntity<SuccessRes> insertUser(@Valid @RequestBody InsertUserReq insertUserReq) throws DuplicateException {

        log.debug("회원가입 정보 = {} ", insertUserReq.toString());

        userService.insertUser(insertUserReq);
        SuccessRes successRes = SuccessRes.builder().message(SUCCESS).build();

        return new ResponseEntity<>(successRes, HttpStatus.OK);
    }

    /**
     * 로그인
     * @param loginReq 로그인을 요청한 사용자의 이메일, 비밀번호
     * @param httpServletRequest
     * @param httpServletResponse
     * @return 로그인 한 사용자의 정보 중 상시 화면에 노출되어야 하는 정보를 {@code ResponseEntity}로 반환합니다
     * @throws NotFoundException
     */
    @ApiOperation(value = "로그인") // 해당 Api의 설명
    @PostMapping("/login")
    public ResponseEntity<LoginRes> loginUser(@RequestBody LoginReq loginReq, HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) throws NotFoundException {

        log.debug("로그인 요청 들어옴.");

        HttpStatus status = null;
        LoginRes loginRes = null; // 리턴값

        User loginUser = userService.loginUser(loginReq.getEmail(), loginReq.getPassword());

        Date now = new Date();

        // access 토큰 발급
        AuthToken accessToken = tokenProvider.createAuthToken(
                loginUser.getId(), // access 토큰에 user pk 저장
                loginUser.getNickname(),
                "ROLE_USER",
                new Date(now.getTime() + appProperties.getAuth().getTokenExpiry())
        );

        // refreshToken 기한
        long refreshTokenExpiry = appProperties.getAuth().getRefreshTokenExpiry();

        // refresh 토큰 발급
        AuthToken refreshToken = tokenProvider.createAuthToken(
                appProperties.getAuth().getTokenSecret(),
                new Date(now.getTime() + refreshTokenExpiry)
        );

        log.debug("일반 로그인 user id(PK) : {}, 닉네임 : {}", loginUser.getId(), loginUser.getNickname());
        log.debug("일반 user 로그인 accessToken 정보 : {}", accessToken.getToken());
        log.debug("일반 user 로그인 refreshToken 정보 : {}", refreshToken.getToken());

        // refresh token DB에 저장
        userService.saveRefreshToken(loginUser.getId(), refreshToken.getToken());

        loginRes = LoginRes.builder()
                .nickname(loginUser.getNickname())
                .picture(loginUser.getPicture())
                .level(loginUser.getLevel())
                .experience(loginUser.getExperience())
                .accessToken(accessToken.getToken())
                .message(SUCCESS)
                .build();

        // 쿠키 기한
        int cookieMaxAge = (int) refreshTokenExpiry / 60;

        // 쿠키를 왜 delete를 하는지는 잘 모르겠음. 찾아봐야겠음.
        CookieUtil.deleteCookie(httpServletRequest, httpServletResponse, REFRESH_TOKEN);
        // response에 쿠키 담아줌.
        CookieUtil.addCookie(httpServletResponse, REFRESH_TOKEN, refreshToken.getToken(), cookieMaxAge);

        status = HttpStatus.OK;


        return new ResponseEntity<LoginRes>(loginRes, status);
    }

    /**
     * 로그아웃
     * @param httpServletRequest
     * @param httpServletResponse
     * @return 성공 시 성공메시지를 {@code ResponseEntity}로 반환합니다
     * @throws NotFoundException
     */
    @ApiOperation(value = "로그아웃") // 해당 Api의 설명
    @GetMapping("/logout")
    public ResponseEntity<SuccessRes> logoutUser(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) throws NotFoundException {

        User user = (User) httpServletRequest.getAttribute("user"); // 로그아웃 요청한 user

        HttpStatus status = HttpStatus.OK;

        userService.deleteRefreshToken(user.getId());
        CookieUtil.deleteCookie(httpServletRequest, httpServletResponse, REFRESH_TOKEN);
        SuccessRes successRes = SuccessRes.builder().message(SUCCESS).build();

        return new ResponseEntity<>(successRes, status);
    }

    /**
     * Access Token 재발급
     * @param httpServletRequest
     * @return 재발급 된 access token을 {@code ResponseEntity}로 반환합니다
     * @throws UnAuthorizedException
     */
    @ApiOperation(value = "Access Token 재발급", notes = "만료된 access token을 재발급받는다.", response = Map.class)
    @GetMapping("/refresh")
    public ResponseEntity<RefreshTokenRes> refreshToken(HttpServletRequest httpServletRequest)
            throws UnAuthorizedException {
        User user = (User) httpServletRequest.getAttribute("user"); // access token 재발급 요청한 user

        // refresh token
        String refreshToken = CookieUtil.getCookie(httpServletRequest, REFRESH_TOKEN)
                .map(Cookie::getValue)
                .orElse(null);

        log.debug("쿠키에 담긴 refresh token : {}", refreshToken);

        AuthToken authRefreshToken = tokenProvider.convertAuthToken(refreshToken);

        if (authRefreshToken.validate() == false || user.getRefreshToken() == null) {
            log.debug("유효하지 않은 refresh token 입니다.");
            throw new UnAuthorizedException("유효하지 않은 refresh token 입니다.");
        }

        Date now = new Date();

        // access 토큰 발급
        AuthToken accessToken = tokenProvider.createAuthToken(
                user.getId(), // access 토큰에 user pk 저장
                user.getNickname(),
                "ROLE_USER",
                new Date(now.getTime() + appProperties.getAuth().getTokenExpiry())
        );

        log.debug("정상적으로 액세스토큰 재발급!!!");
        RefreshTokenRes refreshTokenRes = RefreshTokenRes.builder().message(SUCCESS).accessToken(accessToken.getToken()).build();
        HttpStatus status = HttpStatus.OK;


        return new ResponseEntity<RefreshTokenRes>(refreshTokenRes, status);
    }
    
    /**
     * 회원 정보 조회 - 일반, 카카오톡 사용자 모두 조회할 수 있도록 email, kakaoId 모두 반환해줍니다.
     * @param httpServletRequest
     * @return 해당 메서드를 호출한 사용자의 정보를 {@code ResponseEntity}로 반환합니다
     */
    @ApiOperation(value = "회원 정보 조회") // 해당 Api의 설명
    @GetMapping("/profile")
    public ResponseEntity<GetUserRes> getUser(HttpServletRequest httpServletRequest) {
        User user = (User) httpServletRequest.getAttribute("user");

        GetUserRes getUserRes = userService.getUser(user.getId());

        return new ResponseEntity<GetUserRes>(getUserRes, HttpStatus.OK);
    }

    // 프로필 수정
    @ApiOperation(value = "프로필 수정")
    @PutMapping("/profile")
    public ResponseEntity<SuccessRes> updateUser(@RequestBody UpdateUserReq updateUserReq, HttpServletRequest httpServletRequest) throws NotFoundException, DuplicateException {

        User user = (User) httpServletRequest.getAttribute("user");

        userService.updateUser(updateUserReq, user.getId());

        SuccessRes successRes = SuccessRes.builder().message(SUCCESS).build();

        return new ResponseEntity<>(successRes, HttpStatus.OK);

    }

    // 게임 결과 경험치 반영
    @ApiOperation(value = "게임 결과 경험치 반영")
    @PutMapping("/game/{experience}")
    public ResponseEntity<UpdateExperienceRes> updateExperience(@PathVariable("experience") int experience, HttpServletRequest httpServletRequest) {
        User user = (User) httpServletRequest.getAttribute("user");

        UpdateExperienceRes updateExperienceRes = userService.updateExperience(user.getId(), experience);

        return new ResponseEntity<UpdateExperienceRes>(updateExperienceRes, HttpStatus.OK);

    }

    // 실시간 랭킹 조회
    @ApiOperation(value = "실시간 랭킹 조회")
    @GetMapping("/ranking")
    public ResponseEntity<List<GetTopTenUserRes>> getTopTenUser() throws NotFoundException {
        return new ResponseEntity<List<GetTopTenUserRes>>(userService.getTopTenUser(), HttpStatus.OK);
    }

}
