package com.example.mijang.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * {@code @Async} 를 켠다.
 *
 * <p>쓰는 곳은 재설정 메일 발송 한 곳이다. 발송을 요청 안에서 하면 가입된 주소일 때만
 * SMTP 왕복(수백 ms~수 초)이 붙어, 응답이 같아도 <b>걸린 시간으로 가입 여부가 드러난다.</b>
 * 로그인 실패 문구를 하나로 합쳐 막아 둔 것(미장-auth-구현 2.5)이 그쪽으로 새는 셈이라
 * 발송을 응답 뒤로 넘긴다.
 *
 * <p><b>실행기를 따로 만들지 않는다.</b> {@code Executor} 타입 빈을 하나라도 선언하면
 * 스프링 부트가 기본으로 주던 {@code applicationTaskExecutor} 가 사라진다. 그러면 MVC 의
 * 비동기 응답(SSE 등)이 요청마다 새 스레드를 만드는 방식으로 내려앉는다. 실제로 빈 목록을
 * 찍어 확인했다.
 *
 * <p>부트 기본 실행기는 큐가 무제한이라 넘쳐서 호출한 스레드로 되돌아오는 일이 없다.
 * 직접 만든 작은 풀에 CallerRuns 를 걸었더니, 큐가 차면 발송이 다시 요청 스레드에서
 * 돌아 위에서 막으려던 시간 차이가 되살아났다. 크기 조절이 필요하면 코드가 아니라
 * {@code spring.task.execution.*} 로 한다.
 */
@Configuration
@EnableAsync
public class AsyncConfig {
}
