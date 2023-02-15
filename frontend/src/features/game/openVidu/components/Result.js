import { useNavigate, useLocation } from "react-router-dom";

import "./Result.scss";

const Result = () => {
  const { state } = useLocation();
  const result = state;
  console.log(state);

  const ranking = result.sort((a, b) => b.score - a.score);
  console.log(ranking);

  //랭킹 리스트에서 내 점수 찾기
  // ranking.map((it) => {

  // })

  const crown = require("../../../../assets/result/crown.png");

  let navigate = useNavigate();

  const exitButtonHandler = async () => {
    // try {
    //   const data = await axios.put(`/api/user/game/${score}`, {
    //     experience: score,
    //   });
    //   console.log("경험치를 성공적으로 전달했습니다.", data);
    // } catch {
    //   console.log("경험치 넘기기 실패");
    // }
    navigate("/");
  };

  return (
    <div className="resultContainer">
      <h1 className="resultGameTitle">✨ 게임 결과 ✨</h1>
      <div className="resultBox">
        <div className="rankingBox">
          {ranking.map((it) => (
            <div className="rankingItem">
              <div
                className="rankingCrown"
                style={{
                  visibility: ranking.indexOf(it) === 0 ? "" : "hidden",
                }}
              >
                <img src={crown} alt="" />
              </div>

              <div className="rank">{ranking.indexOf(it) + 1}등</div>
              <div className="profilePic">
                <img src={it.image} alt="profile" />
              </div>
              <div className="nickName">{it.nickname}</div>
              <div className="score">{it.score}점</div>
            </div>
          ))}
        </div>
      </div>
      <button onClick={exitButtonHandler} className="exitButton">
        나가기
      </button>
    </div>
  );
};

export default Result;
