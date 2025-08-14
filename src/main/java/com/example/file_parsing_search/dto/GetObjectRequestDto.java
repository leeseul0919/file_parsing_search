package com.example.file_parsing_search.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class GetObjectRequestDto {
    // Todo: 예외 커스텀할 때 filepath 비어있는거랑 geometry 부분 비어있는거랑
    // Todo: 그리고 예외 커스텀할 때 geometryInfo type이 "Circle", "Rectangle", "Polygon" 이외에 것들에 대한거 신경쓰기
    // Todo: Range Type이 Polygon일 때 첫 좌표랑 마지막 좌표가 같은지도 확인

    @NotBlank(message = "filePath는 필수입니다.")
    private String filePath;

    @NotNull(message = "geometry는 필수입니다.")
    private GeometryInfo geometryInfo;

    public GetObjectRequestDto() { }
}
