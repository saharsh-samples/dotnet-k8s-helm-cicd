FROM mcr.microsoft.com/dotnet/sdk:3.1 AS build

COPY . /build
WORKDIR /build

RUN dotnet publish -c Release -o out

FROM mcr.microsoft.com/dotnet/aspnet:3.1

WORKDIR /app
COPY --from=build /build/out .

ENV ASPNETCORE_URLS "http://*:8080"
EXPOSE 8080

ENTRYPOINT ["dotnet", "sample-dotnet-app.dll"]
