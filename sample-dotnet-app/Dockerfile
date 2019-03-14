FROM microsoft/dotnet:2.2-sdk AS build

COPY . /build
WORKDIR /build

RUN dotnet publish -c Release -o out

FROM microsoft/dotnet:2.2-aspnetcore-runtime

WORKDIR /app
COPY --from=build /build/out .

ENV ASPNETCORE_URLS "http://*:8080"
EXPOSE 8080

ENTRYPOINT ["dotnet", "sample-dotnet-app.dll"]
