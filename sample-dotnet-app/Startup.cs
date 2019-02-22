using System;
using System.Collections.Generic;
using System.Linq;
using System.Threading.Tasks;
using Microsoft.AspNetCore.Builder;
using Microsoft.AspNetCore.Hosting;
using Microsoft.AspNetCore.HttpsPolicy;
using Microsoft.AspNetCore.Mvc;
using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Logging;
using Microsoft.Extensions.Options;
using sample_dotnet_app.Services;
using sample_dotnet_app.Configuration;
using sample_dotnet_app.Filters;

namespace sample_dotnet_app
{
    public class Startup
    {
        public Startup(IConfiguration configuration)
        {
            Configuration = configuration;
        }

        public IConfiguration Configuration { get; }

        // This method gets called by the runtime. Use this method to add services to the container.
        public void ConfigureServices(IServiceCollection services)
        {

            services.AddMvc().SetCompatibilityVersion(CompatibilityVersion.Version_2_2);
            services.Configure<List<AppUser>>(Configuration.GetSection("AppUsers"));
            services.AddSingleton<BasicAuthenticationFilter>();

            var appMetadata = new AppMetadata {
                Name = Configuration.GetValue<string>("APP_NAME", "sample-dotnet-app"),
                Description = Configuration.GetValue<string>("APP_DESCRIPTION", "A sample ASP.NET 2.2 Web API application"),
                Version = Configuration.GetValue<string>("APP_VERSION", "set APP_VERSION in env to override")
            };
            services.AddSingleton(appMetadata);

            string valuesServiceType = Configuration.GetSection("VALUES_SERVICE_TYPE").Get<string>();
            if(valuesServiceType == "simple") {
                services.AddSingleton<IValuesService, SimpleValuesService>();
            } else {
                services.AddSingleton<IValuesService, DefaultValuesService>();
            }

            services.AddHealthChecks();
        }

        // This method gets called by the runtime. Use this method to configure the HTTP request pipeline.
        public void Configure(IApplicationBuilder app, IHostingEnvironment env)
        {
            app.UseHealthChecks("/health");
            app.UseMvc();
        }
    }
}
