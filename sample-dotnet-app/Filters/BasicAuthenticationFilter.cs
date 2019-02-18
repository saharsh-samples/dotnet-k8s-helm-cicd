using System.Collections.Generic;
using Microsoft.AspNetCore.Mvc;
using Microsoft.AspNetCore.Mvc.Filters;
using Microsoft.Extensions.Options;
using sample_dotnet_app.Configuration;

namespace sample_dotnet_app.Filters {

    public class BasicAuthenticationFilter : ActionFilterAttribute {

        private Dictionary<string,AppUser> AppUsers = new Dictionary<string, AppUser>();

        public BasicAuthenticationFilter(IOptions<List<AppUser>> appUsers) {
            foreach (var appUser in appUsers.Value) {
                AppUsers.Add(appUser.Id, appUser);
            }
        }

        public override void OnActionExecuting(ActionExecutingContext context) {

            Microsoft.Extensions.Primitives.StringValues authorizationHeader = "";
            bool headerPresent = context.HttpContext.Request.Headers.TryGetValue("Authorization", out authorizationHeader);

            if (headerPresent) {

                // extract and parse header value
                var headerValue = authorizationHeader.ToString();
                var headerParts = headerValue.Split(':');

                // authenticate
                if(headerParts.Length == 2) {
                    AppUser matchingUser = null;
                    bool found = AppUsers.TryGetValue(headerParts[0], out matchingUser);
                    if(found && headerParts[1] == matchingUser.Password) {
                        return;
                    }
                }
            }

            // reaching here means authentication failed
            context.Result = new UnauthorizedResult();

        }
    }

}
